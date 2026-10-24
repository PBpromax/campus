import taskTransitionsTest from './TaskTransitions.test';
import humanHelpTest from './HumanHelp.test';
import toolApiTest from './ToolApi.test';
import trustedToolsTest from './TrustedTools.test';
import aiStreamTest from './AiStream.test';
import cardProtocolTest from './CardProtocol.test';
import needModelTest from './NeedModel.test';

export default function testsuite() {
  taskTransitionsTest();
  needModelTest();
  cardProtocolTest();
  aiStreamTest();
  trustedToolsTest();
  toolApiTest();
  humanHelpTest();
}
