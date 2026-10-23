import { describe, it, expect } from '@ohos/hypium';
import { NeedCategory, NeedDraft, NeedField, NeedSession, DomainCatalog, emptySession } from '../../../main/ets/domain/NeedModels';
import { allowedKeys, buildCards, decodeCards, validateCards, allowsAction } from '../../../main/ets/ui/CardProtocol';

function fixture(category: NeedCategory, missing: string[]): NeedSession {
  const session = emptySession();
  session.id = 11; session.revision = 2; session.activeCategory = category; session.routePending = false;
  const draft: NeedDraft = { id: 5, category: category, revision: 2, confirmedRevision: null, fields: {}, provenance: {}, contexts: [],
    assessment: { ready: missing.length === 0, missing: missing, conflicts: [] } };
  session.drafts = [draft];
  return session;
}
function catalog(category: NeedCategory): DomainCatalog[] {
  const fields = allowedKeys(category).map((key: string): NeedField => {
    return { key: key, label: key, type: 'text', options: [] };
  });
  return [{ category: category, fields: fields }];
}
export default function cardProtocolTest(): void {
  describe('CardProtocolTest', () => {
    const categories: NeedCategory[] = [NeedCategory.RUNNING,NeedCategory.STUDY,NeedCategory.BORROW,NeedCategory.OTHER];
    categories.forEach((category: NeedCategory) => {
      it('validBoundedCardsFor_' + category, 0, () => {
        const s = fixture(category, allowedKeys(category));
        const result = decodeCards(JSON.stringify(buildCards(s)), s, catalog(category));
        expect(result.error).assertEqual('');
        expect(result.schema.cards.length > 0).assertTrue();
      });
    });
    it('fragmentStudyDoesNotAskForPublicationFields', 0, () => {
      const schema = buildCards(fixture(NeedCategory.STUDY, ['context']));
      expect(schema.cards.length).assertEqual(1);
      expect(schema.cards[0].fields.join(',')).assertEqual('context');
      expect(schema.cards[0].actions.includes('review')).assertFalse();
    });
    it('savedDropoffAddsRequiredLocationCard', 0, () => {
      const s = fixture(NeedCategory.RUNNING, ['dropPlace']);
      s.drafts[0].fields['handoff'] = '指定点放置';
      expect(buildCards(s).cards.some((c) => c.fields.includes('dropPlace'))).assertTrue();
    });
    it('rejectsStaleRevisionAndAnotherSession', 0, () => {
      const s = fixture(NeedCategory.BORROW, ['item']); const schema = buildCards(s);
      s.revision++;
      expect(validateCards(schema,s,catalog(NeedCategory.BORROW)).length > 0).assertTrue();
      s.revision--; s.id++;
      expect(validateCards(schema,s,catalog(NeedCategory.BORROW)).length > 0).assertTrue();
    });
    it('rejectsCrossDomainFieldAndExecutableAction', 0, () => {
      const s = fixture(NeedCategory.STUDY, ['context']); const schema = buildCards(s);
      schema.cards[0].fields = ['returnAt'];
      expect(validateCards(schema,s,catalog(NeedCategory.STUDY)).length > 0).assertTrue();
      schema.cards[0].fields = ['context']; schema.cards[0].actions = ['publish'];
      expect(validateCards(schema,s,catalog(NeedCategory.STUDY)).length > 0).assertTrue();
    });
    it('rejectsArbitraryComponentAndUnknownProperty', 0, () => {
      const s = fixture(NeedCategory.STUDY, ['context']); const schema = buildCards(s);
      schema.cards[0].kind = 'WebView';
      expect(validateCards(schema,s,catalog(NeedCategory.STUDY)).length > 0).assertTrue();
      const raw = JSON.stringify(buildCards(s)).replace('"version":1','"version":1,"script":"attack"');
      expect(decodeCards(raw,s,catalog(NeedCategory.STUDY)).error.length > 0).assertTrue();
    });
    it('malformedAndOversizedPayloadsFallBackWithoutCards', 0, () => {
      const s = fixture(NeedCategory.STUDY, ['context']);
      ['null','{}','[]','{bad','a'.repeat(24001)].forEach((raw: string) => {
        const result = decodeCards(raw,s,catalog(NeedCategory.STUDY));
        expect(result.error.length > 0).assertTrue(); expect(result.schema.cards.length).assertEqual(0);
      });
    });
    it('dirtyInputAndPendingRouteBlockReview', 0, () => {
      const s = fixture(NeedCategory.STUDY, []); s.drafts[0].fields['context'] = 'y=-x';
      const schema = buildCards(s); const defs = catalog(NeedCategory.STUDY);
      expect(allowsAction(schema,'review','review',s,defs,false)).assertTrue();
      expect(allowsAction(schema,'review','review',s,defs,true)).assertFalse();
      s.routePending = true;
      expect(allowsAction(schema,'review','review',s,defs,false)).assertFalse();
    });
    it('invalidFieldComponentAndDuplicateCardAreRejected', 0, () => {
      const s = fixture(NeedCategory.STUDY, ['context']); const schema = buildCards(s); const defs = catalog(NeedCategory.STUDY);
      defs[0].fields[0].type = 'javascript';
      expect(validateCards(schema,s,defs).length > 0).assertTrue();
      schema.cards.push(schema.cards[0]);
      expect(validateCards(schema,s,catalog(NeedCategory.STUDY)).length > 0).assertTrue();
    });
  });
}
