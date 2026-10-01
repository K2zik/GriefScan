package dev.k1zik.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaytimeUtilTest {
   @Test
   void parsesHoursMinutesAndDays() {
      assertEquals(30.0, PlaytimeUtil.parseHours("30h", 1.0, null), 0.0001);
      assertEquals(1.5, PlaytimeUtil.parseHours("90m", 1.0, null), 0.0001);
      assertEquals(48.0, PlaytimeUtil.parseHours("2d", 1.0, null), 0.0001);
      assertEquals(12.0, PlaytimeUtil.parseHours("12", 1.0, null), 0.0001);
   }

   @Test
   void fallsBackOnInvalidInput() {
      assertEquals(30.0, PlaytimeUtil.parseHours("nope", 30.0, null), 0.0001);
      assertEquals(30.0, PlaytimeUtil.parseHours(" ", 30.0, null), 0.0001);
   }
}
