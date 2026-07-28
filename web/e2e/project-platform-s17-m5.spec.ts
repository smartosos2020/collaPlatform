import { expect, test, type APIRequestContext } from '@playwright/test'
import { apiBaseUrl, bearer, installSession, loginByApi, type E2eSession } from './support/api'
import { requireIsolatedIdentityFixture } from './support/fixtures'
type Identity={id:string;username:string;displayName:string;password:string}
type Rule={id:string;status:string;version:number}

test.describe('PROJECT-PLATFORM-S17 M5',()=>{
 test('management quotas diagnostics preferences and stage matrix are real and isolated @route-final',async({page,request},testInfo)=>{
  test.setTimeout(480_000);requireIsolatedIdentityFixture()
  const enterprise=await loginByApi(request)
  const enterpriseProfile=await getJson<{id:string}>(request,`${apiBaseUrl}/auth/me`,enterprise)
  const suffix=`s17m5_${Date.now()}_${Math.random().toString(36).slice(2,7)}`
  const ownerIdentity=await createIdentity(request,enterprise,`${suffix}_owner`,'S17 M5 Owner')
  const adminIdentity=await createIdentity(request,enterprise,`${suffix}_admin`,'S17 M5 Space Admin')
  const memberIdentity=await createIdentity(request,enterprise,`${suffix}_member`,'S17 M5 Member')
  const guestIdentity=await createIdentity(request,enterprise,`${suffix}_guest`,'S17 M5 Guest')
  const outsiderIdentity=await createIdentity(request,enterprise,`${suffix}_outsider`,'S17 M5 Outsider')
  const owner=await loginByApi(request,ownerIdentity.username,ownerIdentity.password)
  const admin=await loginByApi(request,adminIdentity.username,adminIdentity.password)
  const member=await loginByApi(request,memberIdentity.username,memberIdentity.password)
  const guest=await loginByApi(request,guestIdentity.username,guestIdentity.password)
  const outsider=await loginByApi(request,outsiderIdentity.username,outsiderIdentity.password)
  let spaceId:string|undefined;let otherSpaceId:string|undefined
  try{
   spaceId=await createSpace(request,owner,suffix,'primary');otherSpaceId=await createSpace(request,owner,suffix,'other')
   await addMember(request,owner,spaceId,adminIdentity.id,'admin')
   await addMember(request,owner,spaceId,memberIdentity.id,'member')
   await addMember(request,owner,spaceId,guestIdentity.id,'guest')
   const createId=`${suffix}-rule`
   const rule=await postJson<Rule>(request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/rules`,owner,{
    schemaVersion:1,requestId:createId,expectedVersion:0,name:`M5 quota ${suffix}`,
    trigger:{schemaVersion:1,type:'event',eventType:'project.work-item.changed',eventVersion:1},
    condition:{schemaVersion:1,kind:'compare',reference:'event.aggregateId',operator:'exists'},
    actions:[{schemaVersion:1,actionType:'send_notification',config:{
     recipientId:ownerIdentity.id,title:`M5 ${suffix}`,body:'route final'}}],
   },createId)
   await postJson(request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/publish`,owner,
    {schemaVersion:1,requestId:`${suffix}-publish`,expectedVersion:1,action:'publish'},`${suffix}-publish`)
   await postJson(request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/lifecycle`,owner,
    {schemaVersion:1,requestId:`${suffix}-enable`,expectedVersion:2,action:'enable'},`${suffix}-enable`)
   const executeId=`${suffix}-execute`
   await postJson(request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/rules/${rule.id}/execute`,owner,
    {schemaVersion:1,requestId:executeId,dryRun:false,event:{aggregateId:crypto.randomUUID(),spaceId}},executeId)
   const management=await getJson<{
    healthy:boolean;rules:{rules:Rule[]};executions:{runs:Array<{id:string}>}
    quotas:Array<{quotaType:string;quotaKey:string;version:number;pausedUntil?:string}>
    preference:{version:number;defaultFilter:string}
   }>(request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/management`,owner)
   expect(management.rules.rules.some(x=>x.id===rule.id)).toBeTruthy()
   expect(management.executions.runs.length).toBeGreaterThan(0)
   expect(management.quotas.map(x=>x.quotaType).sort()).toEqual(['action','actor','rule','space'])
   const quota=management.quotas[0]
   const pauseId=`${suffix}-pause`
   const pauseUntil=new Date(Date.now()+3600000).toISOString()
   const paused=await postJson<{version:number;pausedUntil:string}>(
    request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/management/quota`,owner,{
     schemaVersion:1,requestId:pauseId,quotaType:quota.quotaType,quotaKey:quota.quotaKey,
     action:'pause',pausedUntil:pauseUntil,
     reason:'pause automation during route final verification',expectedVersion:quota.version,
    },pauseId)
   expect(paused.pausedUntil).toBeTruthy()
   const replay=await postJson<{version:number}>(
    request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/management/quota`,owner,{
     schemaVersion:1,requestId:pauseId,quotaType:quota.quotaType,quotaKey:quota.quotaKey,
     action:'pause',pausedUntil:pauseUntil,
     reason:'pause automation during route final verification',expectedVersion:quota.version,
    },pauseId)
   expect(replay.version).toBe(paused.version)
   await postJson(request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/management/quota`,owner,{
    schemaVersion:1,requestId:`${suffix}-resume`,quotaType:quota.quotaType,quotaKey:quota.quotaKey,
    action:'resume',reason:'resume automation after route final verification',expectedVersion:paused.version,
   },`${suffix}-resume`)
   const prefId=`${suffix}-pref`
   const pref=await postJson<{version:number;defaultFilter:string}>(
    request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/management/preference`,member,
    {schemaVersion:1,requestId:prefId,compactMode:false,defaultFilter:'failed',expectedVersion:0},prefId)
   expect(pref.defaultFilter).toBe('failed')
   expect(pref.version).toBe(1)
   for(const session of [owner,admin,member,guest]){
    const view=await getJson<{rules:{rules:Rule[]}}>(
     request,`${apiBaseUrl}/project-spaces/${spaceId}/automation/management`,session)
    expect(view.rules.rules.some(x=>x.id===rule.id)).toBeTruthy()
   }
   for(const session of [outsider,enterprise]){
    const hidden=await request.get(`${apiBaseUrl}/project-spaces/${spaceId}/automation/management`,{headers:bearer(session)})
    expect([403,404]).toContain(hidden.status());expect(await hidden.text()).not.toContain(suffix)
   }
   const cross=await getJson<{rules:{rules:Rule[]};quotas:unknown[]}>(
    request,`${apiBaseUrl}/project-spaces/${otherSpaceId}/automation/management`,owner)
   expect(cross.rules.rules).toHaveLength(0);expect(cross.quotas).toHaveLength(0)
   await installSession(page,owner);await page.goto(`/project-spaces/${spaceId}/work-items`)
   await expect(page.getByTestId('automation-management-panel')).toBeVisible()
   await expect(page.getByTestId('automation-management-panel')).toContainText('4')
   for(const width of [1440,1366,820]){
    await page.setViewportSize({width,height:900})
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
   }
   await page.screenshot({path:testInfo.outputPath('s17-m5-management-820.png'),fullPage:true})
   await installSession(page,admin);await page.goto(`/project-spaces/${spaceId}/work-items`)
   await expect(page.getByTestId('automation-management-panel').getByRole('button',{name:'暂停'}).first()).toBeEnabled()
   await installSession(page,member);await page.goto(`/project-spaces/${spaceId}/work-items`)
   await expect(page.getByTestId('automation-management-panel').getByRole('button',{name:'暂停'}).first()).toBeDisabled()
   await installSession(page,guest);await page.goto(`/project-spaces/${spaceId}/work-items`)
   await expect(page.getByTestId('automation-management-panel').getByRole('button',{name:'暂停'}).first()).toBeDisabled()
  }finally{
   for(const id of [spaceId,otherSpaceId])if(id)await request.post(`${apiBaseUrl}/project-spaces/${id}/settings/archive`,
    {headers:bearer(owner)}).catch(()=>undefined)
   for(const identity of [adminIdentity,memberIdentity,guestIdentity,outsiderIdentity,ownerIdentity])await request.post(
    `${apiBaseUrl}/admin/users/${identity.id}/offboard`,{headers:bearer(enterprise),
     data:{handoverToUserId:enterpriseProfile.id}}).catch(()=>undefined)
  }
 })
})
async function createSpace(request:APIRequestContext,owner:E2eSession,suffix:string,kind:string){
 const r=await request.post(`${apiBaseUrl}/project-spaces`,{headers:bearer(owner),data:{
  spaceKey:`s17-m5-${kind}-${suffix.replaceAll('_','-')}`,name:`S17 M5 ${kind} ${suffix}`,visibility:'private'}})
 expect(r.ok(),await r.text()).toBeTruthy();return(await r.json() as{id:string}).id
}
async function addMember(request:APIRequestContext,owner:E2eSession,spaceId:string,userId:string,roleKey:string){
 const r=await request.post(`${apiBaseUrl}/project-spaces/${spaceId}/members`,{
  headers:{...bearer(owner),'X-Colla-Request-Id':`s17-m5-member-${userId}`},data:{userId,roleKey}})
 expect(r.ok(),await r.text()).toBeTruthy()
}
async function createIdentity(request:APIRequestContext,admin:E2eSession,username:string,displayName:string){
 const password='member123456';const r=await request.post(`${apiBaseUrl}/admin/users`,{headers:bearer(admin),
  data:{username,password,displayName,email:`${username}@example.com`,roleCode:'member'}})
 expect(r.ok(),await r.text()).toBeTruthy();return{...(await r.json() as Omit<Identity,'password'>),password}
}
async function getJson<T>(request:APIRequestContext,url:string,session:E2eSession){
 const r=await request.get(url,{headers:bearer(session)});expect(r.ok(),`GET ${url}: ${await r.text()}`).toBeTruthy();return await r.json() as T
}
async function postJson<T>(request:APIRequestContext,url:string,session:E2eSession,data:unknown,requestId:string){
 const r=await request.post(url,{headers:{...bearer(session),'X-Colla-Request-Id':requestId},data})
 expect(r.ok(),`POST ${url}: ${await r.text()}`).toBeTruthy();return await r.json() as T
}
