import { abilityDelegatorRegistry, TestRunner } from '@kit.TestKit';
import { Hypium } from '@ohos/hypium';
import layoutReview from '../test/LayoutReview.test';
export default class LayoutReviewRunner implements TestRunner {
  onPrepare(): void {}
  onRun(): void { Hypium.hypiumTest(abilityDelegatorRegistry.getAbilityDelegator(),abilityDelegatorRegistry.getArguments(),layoutReview); }
}
