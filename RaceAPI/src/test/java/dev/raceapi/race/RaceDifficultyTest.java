package dev.raceapi.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaceDifficultyTest {

    @Test
    void mapsVeryEasy() {
        assertEquals("race.difficulty.very_easy", Race.difficultyKey(-3));
        assertEquals("race.difficulty.very_easy", Race.difficultyKey(-10));
    }

    @Test
    void mapsEasy() {
        assertEquals("race.difficulty.easy", Race.difficultyKey(-1));
        assertEquals("race.difficulty.easy", Race.difficultyKey(-2));
    }

    @Test
    void mapsNeutral() {
        assertEquals("race.difficulty.neutral", Race.difficultyKey(0));
    }

    @Test
    void mapsHard() {
        assertEquals("race.difficulty.hard", Race.difficultyKey(1));
        assertEquals("race.difficulty.hard", Race.difficultyKey(2));
    }

    @Test
    void mapsVeryHard() {
        assertEquals("race.difficulty.very_hard", Race.difficultyKey(3));
        assertEquals("race.difficulty.very_hard", Race.difficultyKey(10));
    }
}
