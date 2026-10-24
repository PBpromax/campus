import aiStreamTest from './AiStream.test';
import cardProtocolTest from './CardProtocol.test';
import needModelTest from './NeedModel.test';

export default function testsuite() {
  needModelTest();
  cardProtocolTest();
  aiStreamTest();
}
