package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.application.AutomationWebhookPolicy.*;
import static com.colla.platform.modules.project.domain.AutomationConnectorModels.*;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.AutomationCredentialResolver;
import com.colla.platform.modules.project.domain.AutomationConnectorModels.*;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.AutomationConnectorRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationConnectorService {
    private static final Pattern REQUEST_ID=Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Pattern CREDENTIAL=Pattern.compile("^[A-Za-z0-9._:/-]{1,240}$");
    private final AutomationConnectorRepository repository;
    private final ProjectSpaceRepository spaces;
    private final List<AutomationCredentialResolver> credentials;
    private final AuditLog audit;
    private final TransactionalOutbox outbox;
    private final HttpClient http=HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public AutomationConnectorService(
        AutomationConnectorRepository repository, ProjectSpaceRepository spaces,
        List<AutomationCredentialResolver> credentials, AuditLog audit, TransactionalOutbox outbox
    ){this.repository=repository;this.spaces=spaces;this.credentials=credentials;this.audit=audit;this.outbox=outbox;}

    public ConnectorFoundation get(CurrentUser user,UUID spaceId){
        visible(user,spaceId);
        var connectors=repository.list(user.workspaceId(),spaceId,MAX_CONNECTORS+1);
        var deliveries=repository.deliveries(user.workspaceId(),spaceId,MAX_DELIVERIES+1);
        return new ConnectorFoundation(SCHEMA_VERSION,
            connectors.stream().limit(MAX_CONNECTORS).toList(),
            deliveries.stream().limit(MAX_DELIVERIES).toList(),
            connectors.size()>MAX_CONNECTORS,deliveries.size()>MAX_DELIVERIES,
            MAX_PAYLOAD_BYTES,CONNECT_TIMEOUT_MS,RESPONSE_TIMEOUT_MS);
    }
    @Transactional public Connector save(CurrentUser user,UUID spaceId,SaveConnectorCommand command){
        configurable(user,spaceId); validate(command);
        UUID id=command.connectorId()==null?UUID.randomUUID():command.connectorId();
        Connector connector=repository.save(user.workspaceId(),spaceId,id,command.expectedVersion(),
            command.name().trim(),target(command.targetUri()).toString(),
            command.credentialReference());
        emit(user,spaceId,id,"saved");
        return connector;
    }
    @Transactional public Delivery test(
        CurrentUser user,UUID spaceId,UUID connectorId,TestDeliveryCommand command
    ){
        configurable(user,spaceId);
        if(command==null||command.schemaVersion()!=1||!request(command.requestId())
            ||command.payload()==null||command.payload().getBytes(StandardCharsets.UTF_8).length>MAX_PAYLOAD_BYTES)
            throw failure("AUTOMATION_DELIVERY_INVALID","Delivery input is invalid");
        Connector connector=repository.find(user.workspaceId(),spaceId,connectorId)
            .orElseThrow(()->failure("NOT_FOUND_OR_HIDDEN","Connector unavailable"));
        URI target=target(connector.targetUri());
        String hash=hash(command.payload());
        Delivery delivery=repository.beginDelivery(user.workspaceId(),spaceId,connectorId,null,hash,command.requestId());
        if(!hash.equals(delivery.payloadHash()))
            throw failure("AUTOMATION_DELIVERY_REQUEST_CONFLICT","Delivery request input changed");
        if(delivery.attemptCount()>0) return delivery;
        if(command.dryRun()) return repository.recordAttempt(user.workspaceId(),spaceId,delivery.id(),
            "succeeded",null,null,0,false);
        char[] secret=resolve(user,connector.credentialReference());
        try {
            String timestamp=Long.toString(Instant.now().getEpochSecond());
            String signature=sign(secret,timestamp+"."+command.requestId()+"."+command.payload());
            target(target.toString());
            var request=HttpRequest.newBuilder(target).timeout(Duration.ofMillis(RESPONSE_TIMEOUT_MS))
                .header("Content-Type","application/json")
                .header("X-Colla-Timestamp",timestamp).header("X-Colla-Nonce",command.requestId())
                .header("X-Colla-Signature","v1="+signature)
                .POST(HttpRequest.BodyPublishers.ofString(command.payload())).build();
            long started=System.nanoTime();
            try {
                var response=http.send(request,HttpResponse.BodyHandlers.discarding());
                int duration=(int)Math.min(Integer.MAX_VALUE,(System.nanoTime()-started)/1_000_000);
                boolean success=response.statusCode()>=200&&response.statusCode()<300;
                boolean retryable=response.statusCode()==408||response.statusCode()==429||response.statusCode()>=500;
                delivery=repository.recordAttempt(user.workspaceId(),spaceId,delivery.id(),
                    success?"succeeded":"failed",response.statusCode(),
                    success?null:"WEBHOOK_HTTP_"+response.statusCode(),duration,retryable);
            } catch(InterruptedException exception){
                Thread.currentThread().interrupt();
                delivery=repository.recordAttempt(user.workspaceId(),spaceId,delivery.id(),
                    "failed",null,"WEBHOOK_INTERRUPTED",0,true);
            } catch(java.io.IOException exception){
                delivery=repository.recordAttempt(user.workspaceId(),spaceId,delivery.id(),
                    "failed",null,"WEBHOOK_NETWORK_FAILED",0,true);
            }
        } finally {java.util.Arrays.fill(secret,'\0');}
        emit(user,spaceId,delivery.id(),"delivery_"+delivery.status());
        return delivery;
    }
    @Transactional public Delivery govern(CurrentUser user,UUID spaceId,UUID deliveryId,DeliveryGovernanceCommand command){
        configurable(user,spaceId);
        if(command==null||command.schemaVersion()!=1||!request(command.requestId())
            ||!Set.of("replay","abandon").contains(command.action())
            ||command.reason()==null||command.reason().trim().length()<10||command.reason().length()>512)
            throw failure("AUTOMATION_DELIVERY_INVALID","Delivery governance input is invalid");
        Delivery result=repository.govern(user.workspaceId(),spaceId,deliveryId,command.action(),command.reason().trim());
        emit(user,spaceId,deliveryId,command.action());
        return result;
    }
    private char[] resolve(CurrentUser user,String reference){
        if(reference==null||credentials.isEmpty()) throw failure("AUTOMATION_CREDENTIAL_UNAVAILABLE","Credential unavailable");
        return credentials.stream().map(r->r.resolve(user.workspaceId(),user.id(),reference))
            .flatMap(Optional::stream).findFirst()
            .orElseThrow(()->failure("AUTOMATION_CREDENTIAL_UNAVAILABLE","Credential unavailable"));
    }
    private void validate(SaveConnectorCommand c){
        if(c==null||c.schemaVersion()!=1||!request(c.requestId())||c.expectedVersion()<0
            ||(c.connectorId()==null)!=(c.expectedVersion()==0)
            ||c.name()==null||c.name().trim().length()<2||c.name().trim().length()>160
            ||(c.credentialReference()!=null&&!CREDENTIAL.matcher(c.credentialReference()).matches()))
            throw failure("AUTOMATION_CONNECTOR_INVALID","Connector input is invalid");
    }
    private boolean request(String value){return value!=null&&REQUEST_ID.matcher(value).matches();}
    private URI target(String value){
        try{return AutomationWebhookPolicy.validate(value);}
        catch(IllegalArgumentException exception){throw failure(exception.getMessage(),"Webhook target is unavailable");}
    }
    private ProjectSpaceSummary visible(CurrentUser u,UUID s){
        var space=spaces.findById(u.workspaceId(),s,u.id()).orElseThrow(()->failure("NOT_FOUND_OR_HIDDEN","Space unavailable"));
        if(!space.isMember()||"archived".equals(space.status()))throw failure("NOT_FOUND_OR_HIDDEN","Space unavailable");
        return space;
    }
    private void configurable(CurrentUser u,UUID s){
        var space=visible(u,s);
        if(!"active".equals(space.status())||!Set.of("owner","admin").contains(space.currentUserRole()))
            throw failure("FORBIDDEN","Only project space owners and administrators can manage connectors");
    }
    private void emit(CurrentUser user,UUID spaceId,UUID id,String change){
        audit.log(user,"project_automation.connector_"+change,"project_automation_connector",id,
            Map.of("space_id",spaceId.toString(),"change",change));
        outbox.append(user.workspaceId(),"project.automation.connector.changed",
            "project_automation_connector",id,user.id(),Map.of("spaceId",spaceId.toString(),"change",change),
            "automation-connector:"+id+":"+change);
    }
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String sign(char[] secret,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(new String(secret).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
