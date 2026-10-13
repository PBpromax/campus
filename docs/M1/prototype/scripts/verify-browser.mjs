import { chromium } from '@playwright/test'
import { mkdir,writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
const directory=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../evidence')
await mkdir(directory,{recursive:true})
const browser=await chromium.launch(process.env.M1_BROWSER==='chromium'?{headless:true}:{headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'})
const context=await browser.newContext({viewport:{width:1440,height:1050}})
const page=await context.newPage()
const errors=[],businessRequests=[],checks=[]
page.on('pageerror',error=>errors.push(error.message))
page.on('request',request=>{if(['xhr','fetch','websocket'].includes(request.resourceType()))businessRequests.push(request.url())})
const at=id=>page.getByTestId(id)
async function check(name,fn){try{await fn();checks.push({name,passed:true});console.log('PASS '+name)}catch(error){checks.push({name,passed:false,error:error.message});console.log('FAIL '+name+': '+error.message)}}
async function fill(key,value){await at('field-'+key).fill(value);await at('field-'+key).blur()}
async function shot(name){await page.screenshot({path:path.join(directory,name),fullPage:true})}
try{
 await page.goto(process.env.M1_URL || 'http://127.0.0.1:5187')
 await check('twenty scene shortcuts show their distinct missing fields',async()=>{
  for(const [domain,ids] of [['borrow',['B01','B02','B03','B04','B05']],['study',['L01','L02','L03','L04','L05']],['running',['R01','R02','R03','R04','R05']],['other',['O01','O02','O03','O04','O05']]]){
   await at('domain-'+domain).click()
   for(const id of ids){await at(id).click();assert.match(await page.locator('.sample-note').innerText(),/用户原话/);assert.match(await page.locator('.details .hint').innerText(),/还需要确认/)}
  }
 })
 await at('domain-borrow').click()
 await check('unknown compatibility cannot confirm',async()=>assert.equal(await at('confirm').isEnabled(),false))
 await shot('01-borrow-spec.png')
 await fill('spec','USB-C 输入 / HDMI 输出；本人核实电脑视频输出能力')
 await check('explicit spec enables draft confirmation',async()=>{await at('confirm').click();assert.match(await at('outcome').innerText(),/资源可用性/)})
 await check('handoff modification invalidates old confirmation and needs consent',async()=>{
  await at('field-handoff').selectOption('需要送达');assert.equal(await at('outcome').count(),0);assert.equal(await at('confirm').isEnabled(),false);assert.equal(await at('field-pickupPlace').inputValue(),'');assert.equal(await at('field-deliveryConsent').count(),1)
 })
 await fill('pickupPlace','附近某个地方')
 await check('ambiguous receiving location is rejected',async()=>{assert.match(await at('risks').innerText(),/不明确/);assert.equal(await at('confirm').isEnabled(),false)})
 await fill('pickupPlace','示例校区6号宿舍一层门厅')
 await at('field-deliveryConsent').selectOption('待与出借方协商')
 await at('confirm').click()
 await shot('02-borrow-handoff.png')
 await check('changed card contains pending negotiation rather than invented agreement',async()=>{assert.match(await at('confirmation-card').innerText(),/待与出借方协商/);assert.match(await at('outcome').innerText(),/仍须核实/)})
 await fill('returnAt','2026-10-14T19:30')
 await check('return conflict blocks confirmation',async()=>{assert.match(await at('risks').innerText(),/归还早于/);assert.equal(await at('confirm').isEnabled(),false)})
 await shot('03-borrow-time-conflict.png')
 await at('undo').click()
 await check('undo restores time and confirmation stays invalidated',async()=>{assert.equal(await at('field-returnAt').inputValue(),'2026-10-14T20:30');assert.equal(await at('confirm').isEnabled(),true);assert.match(await at('outcome').innerText(),/重新确认/)})
 await at('fixed-form').click()
 await check('fixed form fallback is usable',async()=>{assert.match(await page.locator('.details h2').innerText(),/固定表单/);await fill('returnPlace','示例校区图书馆西门');assert.match(await at('confirmation-card').innerText(),/图书馆西门/)})
 await at('domain-study').click()
 await check('fragmented question starts without a publication card',async()=>{assert.equal(await at('confirmation-card').count(),0);assert.equal(await at('field-reward').count(),0);assert.equal(await at('resolve').isEnabled(),false)})
 await at('message').fill('第二步 x = -2，第三步 -x = 2')
 await at('send').click()
 await at('message').fill('这里为什么要取负号？')
 await at('send').click()
 await check('short follow-up keeps expression and step context',async()=>{assert.match(await at('context-summary').innerText(),/x = -2/);assert.match(await page.locator('.messages').innerText(),/这里为什么/);assert.equal(await at('resolve').isEnabled(),true);assert.equal(await at('confirmation-card').count(),0)})
 await shot('04-study-fragment.png')
 await at('prepare-publish').click()
 await check('publication asks for original required fields with no invented values',async()=>{assert.equal(await at('confirmation-card').count(),1);for(const key of ['reward','expectedTime','location'])assert.equal(await at('field-'+key).inputValue(),'');assert.equal(await at('confirm').isEnabled(),false)})
 await fill('expectedTime','2026-10-14T20:30');await fill('reward','0');await fill('location','示例校区图书馆东门')
 await check('zero reward conflict is visible',async()=>{assert.match(await at('risks').innerText(),/大于0/);assert.equal(await at('confirm').isEnabled(),false)})
 await fill('reward','5');await at('confirm').click()
 await check('simulation returns no real business identifier',async()=>assert.match(await at('outcome').innerText(),/未产生真实需求ID/))
 await shot('05-study-publication.png')
 await at('resume').click();await at('resolve').click()
 await check('user resolution and reset work',async()=>{assert.match(await at('outcome').innerText(),/用户声明/);assert.equal(await at('message').isEnabled(),false);await at('reset').click();assert.equal(await at('message').isEnabled(),true);assert.equal(await at('field-context').inputValue(),'')})
 await at('handoff').click()
 await check('human handoff explicitly stays simulated',async()=>{assert.match(await at('outcome').innerText(),/没有发送给真实人员/);assert.equal(await at('message').isEnabled(),false)})
 await at('reset').click();await at('cancel').click()
 await check('draft cancellation is local and resettable',async()=>{assert.match(await at('outcome').innerText(),/未调用真实取消接口/);await at('reset').click()})
 await page.setViewportSize({width:390,height:844})
 await check('mobile layout has no page overflow',async()=>{assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true)})
 await shot('06-mobile-study.png')
 await page.setViewportSize({width:1440,height:1050})
 await at('domain-running').click()
 await check('courier dropoff asks for a concrete place',async()=>{assert.equal(await at('confirm').isEnabled(),false);await fill('dropPlace','附近某个地方');assert.match(await at('risks').innerText(),/放置点不明确/);await fill('dropPlace','示例校区6号宿舍门厅左侧置物架');assert.equal(await at('confirm').isEnabled(),true)})
 await shot('07-running-dropoff.png')
 await check('courier handoff change clears confirmation and old proof',async()=>{await at('confirm').click();await at('field-handoff').selectOption('当面交付');assert.equal(await at('outcome').count(),0);assert.equal(await at('field-dropPlace').count(),0);assert.equal(await at('confirm').isEnabled(),false);await at('field-completionProof').selectOption('当面确认');assert.equal(await at('confirm').isEnabled(),true)})
 await at('R02').click()
 await check('quiet classroom delivery rejects phone notification',async()=>{await at('field-notice').selectOption('电话');assert.match(await at('risks').innerText(),/静默/);await at('field-notice').selectOption('静默消息');assert.equal(await at('confirm').isEnabled(),true)})
 await at('domain-other').click()
 await check('other assistance requires a verifiable scope',async()=>{await at('O05').click();assert.equal(await at('confirm').isEnabled(),false);await fill('completionCriteria','帮忙');assert.match(await at('risks').innerText(),/标准仍含糊/);await fill('completionCriteria','指定物料清单整理完成，由发布者核对');assert.equal(await at('confirm').isEnabled(),true)})
 await shot('08-other-scope.png')
 await check('multi-helper assistance needs an organization decision',async()=>{await at('O02').click();await fill('helperCount','2');assert.equal(await at('confirm').isEnabled(),false);await at('field-splitMode').selectOption('拆分为单人任务（模拟）');assert.equal(await at('confirm').isEnabled(),true);await at('prepare-publish').click();await fill('expectedTime','2026-10-14T21:00');await fill('reward','5');await fill('location','示例校区活动室');await at('confirm').click();await at('confirmation-card').locator('summary').click();assert.match(await at('confirmation-card').innerText(),/simulated_split/)})
 const fixes={R01:['handling','一件约5千克，只送到门厅，能力待协商'],R02:['notice','静默消息'],R03:['pickupPlace','示例校区北门快递站柜台'],R04:['language','中英双语'],R05:['dropPlace','示例校区6号宿舍门厅左侧置物架'],O01:['resourceRequirements','用户自己的电脑与系统版本，不提供密码'],O02:['helperCount','1'],O03:['servicePlace','线上，通过平台任务消息联系'],O04:['serviceStart','2026-10-14T19:00'],O05:['completionCriteria','按指定清单整理物料，由发布者核对']}
 await check('all ten new scenarios reach a category-correct publication preview',async()=>{
  for(const domain of ['running','other']) {
   await at('domain-'+domain).click()
   for(const [id,[key,value]] of Object.entries(fixes).filter(([id])=>id.startsWith(domain==='running'?'R':'O'))) {
    await at(id).click()
    if(['notice','language'].includes(key))await at('field-'+key).selectOption(value);else await fill(key,value)
    assert.equal(await at('confirm').isEnabled(),true,id)
    await at('prepare-publish').click();await fill('expectedTime','2026-10-19T21:00');await fill('reward','5');await fill('location','示例校区活动室');await at('confirm').click()
    assert.match(await at('outcome').innerText(),/未产生真实需求ID/)
    await at('confirmation-card').locator('summary').click();assert.match(await at('confirmation-card').innerText(),new RegExp(domain.toUpperCase()))
   }
  }
 })
 await at('domain-auto').click()
 await check('unified entry can recognize courier needs without auto-publishing',async()=>{await at('message').fill('帮我代取快递');await at('send').click();assert.equal(await at('routing-card').count(),1);assert.match(await at('route-running').innerText(),/候选/);assert.equal(await at('prepare-publish').isEnabled(),false)})
 await shot('09-unified-routing.png')
 await at('route-running').click()
 await check('chosen courier route asks for its own fields',async()=>{assert.equal(await at('field-earliestPickup').count(),1);assert.equal(await at('field-returnAt').count(),0);assert.equal(await at('confirm').isEnabled(),false)})
 await at('domain-borrow').click();await fill('spec','USB-C 输入 / HDMI 输出，由本人核实');await at('confirm').click()
 await check('cross-business input requires choice and archives the borrow draft',async()=>{await at('message').fill('现在帮我代取快递');await at('send').click();assert.equal(await at('confirm').isEnabled(),false);await at('route-running').click();assert.equal(await at('saved-drafts').count(),1);assert.equal(await at('field-returnAt').count(),0);const saved=page.getByTestId(/^restore-/).filter({hasText:'物品借用'}).last();await saved.click();assert.equal(await at('field-returnAt').inputValue(),'2026-10-14T20:30');assert.match(await at('outcome').innerText(),/旧确认失效/)})
 await shot('10-restored-draft.png')
 await at('domain-auto').click()
 await check('unknown input has manual routes and never defaults to OTHER',async()=>{await at('message').fill('帮我处理一下那个事');await at('send').click();assert.match(await at('routing-card').innerText(),/不够清楚/);for(const domain of ['borrow','study','running','other'])assert.doesNotMatch(await at('route-'+domain).innerText(),/候选/)})
 await at('domain-auto').click()
 await check('mixed goals remain separate until the user chooses',async()=>{await at('message').fill('想借相机，还需要代取快递');await at('send').click();assert.match(await at('route-borrow').innerText(),/候选/);assert.match(await at('route-running').innerText(),/候选/);await at('route-running').click();assert.equal(await at('field-returnAt').count(),0)})
 await page.setViewportSize({width:390,height:844})
 await check('four-business mobile entry fits the screen',async()=>{assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);for(const domain of ['borrow','study','running','other'])assert.equal(await at('domain-'+domain).count(),1)})
 await shot('11-mobile-running.png')
 await check('no page exceptions, production requests or browser memory',async()=>{assert.deepEqual(errors,[]);assert.deepEqual(businessRequests,[]);assert.deepEqual(await page.evaluate(()=>[localStorage.length,sessionStorage.length]),[0,0])})
}finally{
 await writeFile(path.join(directory,'browser-results.json'),JSON.stringify({timestamp:new Date().toISOString(),browser:browser.version(),checks,errors,businessRequests},null,2)+'\n')
 await browser.close()
}
if(checks.some(c=>!c.passed))process.exitCode=1
