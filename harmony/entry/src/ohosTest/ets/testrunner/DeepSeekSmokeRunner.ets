import { abilityDelegatorRegistry, TestRunner } from '@kit.TestKit';
import { Hypium } from '@ohos/hypium';
import deepSeekSmoke from '../test/DeepSeekSmoke.test';
export default class DeepSeekSmokeRunner implements TestRunner {
  onPrepare(): void {}
  onRun(): void { Hypium.hypiumTest(abilityDelegatorRegistry.getAbilityDelegator(), abilityDelegatorRegistry.getArguments(), deepSeekSmoke); }
}
