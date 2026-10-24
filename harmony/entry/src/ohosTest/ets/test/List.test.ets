import humanHelpTest from './HumanHelp.test';
import toolApiTest from './ToolApi.test';
import trustedToolsTest from './TrustedTools.test';
import aiStreamTest from './AiStream.test';
import cardProtocolTest from './CardProtocol.test';
import needModelTest from './NeedModel.test';

export default function testsuite() {
  needModelTest();
  cardProtocolTest();
  aiStreamTest();
  trustedToolsTest();
  toolApiTest();
  humanHelpTest();
}
