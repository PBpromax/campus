package com.example.campushelp.ai;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnswerTextTest {
    String clean(String... parts) {var filter=new AnswerText();var out=new StringBuilder();for(var s:parts) filter.append(s,out::append);filter.finish(out::append);return out.toString();}
    @Test void blocksReasoningAcrossEveryCharacterBoundary() {assertEquals("请问几点取件？",clean("<think>private planning</think>请问几点取件？".split("")));}
    @Test void handlesCaseAndMultipleBlocks() {assertEquals("答案完成",clean("<ANALYSIS>内部</ANALYSIS>答案<reasoning>推理</reasoning>完成"));}
    @Test void neverFlushesUnclosedReasoning() {assertEquals("已收到",clean("已收到<think>unfinished private text"));assertEquals("",clean("<thi"));}
    @Test void preservesTeachingAndMath() {assertEquals("当 x < 0，-x > 0。推导：dy/dx=-1。",clean("当 x < 0，-x > 0。推导：dy/dx=-1。"));assertEquals("x <",clean("x <"));}
}
