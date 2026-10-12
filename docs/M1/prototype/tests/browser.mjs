import { chromium } from 'playwright'
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve as pathResolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const here = dirname(fileURLToPath(import.meta.url))
const evidence = pathResolve(here, '../../evidence')
await mkdir(evidence, { recursive: true })
const baseURL = process.env.M1_URL || 'http://127.0.0.1:5181'
const browser = await chromium.launch({ channel: process.env.M1_BROWSER || 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 })
const failures = [], checks = [], errors = [], requests = []
page.on('pageerror', error => errors.push(error.message))
page.on('dialog', dialog => dialog.accept())
page.on('request', request => { if (!request.url().startsWith(baseURL) && !request.url().startsWith('data:')) requests.push(request.url()) })
async function check(name, action) {
  try { await action(); checks.push({ name, passed: true }); console.log('PASS', name) }
  catch (error) { failures.push(name); checks.push({ name, passed: false, error: error.message }); console.log('FAIL', name, error.message) }
}
async function fresh(business = 'express') {
  await page.goto(baseURL)
  if (business === 'learning') await page.getByRole('button', { name: /^学习求助/ }).click()
}
async function field(key, value) {
  const input = page.locator('#field-' + key)
  const tag = await input.evaluate(el => el.tagName)
  if (tag === 'SELECT') await input.selectOption(value)
  else { await input.fill(value); await input.press('Tab') }
}
async function message(text) { await page.locator('#message-input').fill(text); await page.getByRole('button', { name: '发送消息 ↗' }).click() }
async function fillExpress(mode) {
  await field('campus', '仙林校区'); await field('pickup', '北门驿站'); await field('parcelCount', '2')
  await field('handoffMode', mode); await field('destination', mode === 'face' ? '5号宿舍楼门口' : '5号宿舍楼一楼指定置物架')
  await field('timeWindow', '21:00—21:30'); await field('notice', 'silent')
  if (mode === 'face') await field('contact', '站内消息，到达后联系'); else await field('completion', 'photo')
}
await check('首页明确标注模拟与两个业务入口', async () => {
  await fresh(); assert.equal(await page.getByText('规则原型 · 全部操作为模拟').count(), 1)
  assert.equal(await page.locator('.scene').count(), 5)
  await page.screenshot({ path: pathResolve(evidence, '01-express-start.png'), fullPage: true })
})
await check('十个场景载入并产生澄清或对话回应', async () => {
  for (const business of ['express', 'learning']) {
    await fresh(business)
    for (let i = 0; i < 5; i++) {
      await page.locator('.scene').nth(i).click()
      assert.equal(await page.locator('.message').count(), 2)
      assert.equal(await page.locator('.message.assistant').count(), 1)
    }
  }
})
await check('新生含糊校区保持未知并请求确认', async () => {
  await fresh(); await page.locator('.scene').nth(2).click()
  assert.equal(await page.locator('#field-campus').inputValue(), '')
  assert.match(await page.locator('.message.assistant').last().innerText(), /哪个校区/)
  await page.screenshot({ path: pathResolve(evidence, '02-location-clarification.png'), fullPage: true })
})
await check('当面交付完整约定可确认，修改后需要重确认', async () => {
  await fresh(); await message('请帮我取两个快递'); await fillExpress('face')
  const button = page.getByRole('button', { name: '确认并模拟执行' })
  assert.ok(await button.isEnabled()); await button.click()
  assert.match(await page.locator('.result').innerText(), /未发布真实需求/)
  assert.ok(await button.isDisabled()); await field('timeWindow', '22:00—22:30')
  assert.equal(await page.locator('.result').count(), 0); assert.ok(await button.isEnabled())
  await page.screenshot({ path: pathResolve(evidence, '03-face-confirmation.png'), fullPage: true })
})
await check('改成放置交付需要重新明确地点与完成确认', async () => {
  await field('handoffMode', 'drop'); assert.equal(await page.locator('#field-destination').inputValue(), '')
  assert.ok(await page.getByRole('button', { name: '确认并模拟执行' }).isDisabled())
  await field('destination', '5号宿舍楼一楼指定置物架'); await field('completion', 'photo')
  await page.getByRole('button', { name: '确认并模拟执行' }).click()
  await page.screenshot({ path: pathResolve(evidence, '04-drop-confirmed.png'), fullPage: true })
})
await check('时间冲突阻止执行，明确暂存约定后解除', async () => {
  await fresh(); await message('请帮我取件'); await fillExpress('face'); await field('pickupDeadline', '20:30')
  assert.equal(await page.locator('.conflict').count(), 1)
  assert.ok(await page.getByRole('button', { name: '确认并模拟执行' }).isDisabled())
  await page.getByRole('button', { name: '选择提前取件，再补充临时存放点' }).click()
  await field('storagePlace', '北门驿站已确认的临时寄存柜')
  assert.equal(await page.locator('.conflict').count(), 0)
  assert.ok(await page.getByRole('button', { name: '确认并模拟执行' }).isEnabled())
})
await check('跨语言样本展示双语交接标签', async () => {
  await fresh(); await page.locator('.scene').nth(3).click()
  assert.equal(await page.getByRole('button', { name: /当面交付 · In person/ }).count(), 1)
})
await check('碎片小问题保持连续消息，不出现发布确认卡', async () => {
  await fresh('learning'); await page.locator('.scene').nth(4).click()
  assert.equal(await page.getByRole('region', { name: '确认卡' }).count(), 0)
  assert.match(await page.locator('.message.assistant').last().innerText(), /两步表达式/)
  await message('第二步：y = -cos x；第三步：y′ = sin x')
  await message('再解释慢一点，只想知道这一小步')
  assert.equal(await page.locator('.message').count(), 6)
  assert.match(await page.locator('#field-context').inputValue(), /-cos x/)
  assert.equal(await page.locator('#field-availableTime').count(), 0)
  assert.equal(await page.locator('#field-campus').count(), 0)
  await page.screenshot({ path: pathResolve(evidence, '05-fragment-conversation.png'), fullPage: true })
  await page.getByRole('button', { name: '标记已解决' }).click()
  assert.match(await page.locator('.result').innerText(), /用户反馈/)
})
await check('学习发布与预约按需出现确认，时长不能代替时段', async () => {
  await fresh('learning'); await message('高数链式法则没明白')
  await page.getByRole('button', { name: '模拟发布求助' }).click()
  assert.equal(await page.getByRole('region', { name: '确认卡' }).count(), 1)
  await field('communication', 'async'); assert.ok(await page.getByRole('button', { name: '确认并模拟执行' }).isEnabled())
  await page.getByRole('button', { name: '模拟预约协助' }).click()
  await field('availableTime', '20分钟'); assert.ok(await page.getByRole('button', { name: '确认并模拟执行' }).isDisabled())
  await field('availableTime', '今晚20:00—20:20'); assert.ok(await page.getByRole('button', { name: '确认并模拟执行' }).isEnabled())
  await page.screenshot({ path: pathResolve(evidence, '06-learning-booking.png'), fullPage: true })
})
await check('固定表单可补充非规则识别信息', async () => {
  await fresh('learning'); await page.getByRole('button', { name: '使用固定表单' }).click()
  await field('topic', '数字电路 · 卡诺图化简'); await field('context', '为什么边界格子可以相邻？')
  assert.equal(await page.locator('#field-topic').inputValue(), '数字电路 · 卡诺图化简')
})
await check('模拟接管与撤销阻止继续执行；重置清空上下文', async () => {
  await fresh(); await message('帮我取快递'); await page.getByRole('button', { name: '模拟人工接管' }).click()
  assert.match(await page.locator('.result').innerText(), /队列尚未接入/)
  assert.ok(await page.locator('#message-input').isDisabled()); await page.getByRole('button', { name: '重置对话' }).click()
  assert.equal(await page.locator('.message').count(), 0)
  await message('帮我取一个快递'); await page.getByRole('button', { name: '撤销本次任务' }).click()
  assert.ok(await page.locator('#message-input').isDisabled())
})
await check('移动端无横向溢出且可发送小问题', async () => {
  await page.setViewportSize({ width: 390, height: 844 }); await fresh('learning')
  await message('第二步到第三步怎么来的？')
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await page.screenshot({ path: pathResolve(evidence, '07-mobile-learning.png'), fullPage: true })
})
await check('页面无运行异常、无外部业务请求和持久化数据', async () => {
  assert.deepEqual(errors, []); assert.deepEqual(requests, [])
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0)
})
const report = { checkedAt: new Date().toISOString(), baseURL, browser: await browser.version(), total: checks.length, passed: checks.filter(c => c.passed).length, failures, checks, pageErrors: errors, externalRequests: requests }
await writeFile(pathResolve(evidence, 'browser-results.json'), JSON.stringify(report, null, 2) + '\n')
await browser.close()
console.log(`${report.passed}/${report.total} browser checks passed`)
if (failures.length) process.exitCode = 1
