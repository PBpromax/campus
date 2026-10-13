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
 await check('ten scene shortcuts show their distinct missing fields',async()=>{
  for(const [domain,ids] of [['borrow',['B01','B02','B03','B04','B05']],['study',['L01','L02','L03','L04','L05']]]){
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
 await check('no page exceptions, production requests or browser memory',async()=>{assert.deepEqual(errors,[]);assert.deepEqual(businessRequests,[]);assert.deepEqual(await page.evaluate(()=>[localStorage.length,sessionStorage.length]),[0,0])})
}finally{
 await writeFile(path.join(directory,'browser-results.json'),JSON.stringify({timestamp:new Date().toISOString(),browser:browser.version(),checks,errors,businessRequests},null,2)+'\n')
 await browser.close()
}
if(checks.some(c=>!c.passed))process.exitCode=1
