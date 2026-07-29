package com.colla.platform.modules.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class AutomationManagementFoundationIntegrationTests {
 @Test void migratesPreferencesQuotasClaimsAndGovernanceReceipts() throws Exception {
  try(var postgres=new PostgreSQLContainer<>("postgres:16")){
   postgres.start();
   PGSimpleDataSource ds=new PGSimpleDataSource();ds.setURL(postgres.getJdbcUrl());
   ds.setUser(postgres.getUsername());ds.setPassword(postgres.getPassword());
   Flyway.configure().dataSource((DataSource)ds).locations("classpath:db/migration").load().migrate();
   assertThat(Flyway.configure().dataSource(ds).load().info().current().getVersion().getVersion()).isEqualTo("137");
   try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("""
    select count(*) from information_schema.tables where table_name in
    ('project_automation_management_preferences','project_automation_quota_states',
     'project_automation_quota_receipts','project_automation_governance_receipts')
    """)){r.next();assertThat(r.getInt(1)).isEqualTo(4);}
  }
 }
}
