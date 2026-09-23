package fr.cdaacademy.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LevelCalculatorTest {

    @ParameterizedTest(name = "{0} XP → niveau {1}")
    @CsvSource({"0,1", "49,1", "50,2", "199,2", "200,3", "450,4", "5000,11"})
    void calculeLeNiveau(int xp, int niveauAttendu) {
        assertThat(LevelCalculator.level(xp)).isEqualTo(niveauAttendu);
    }

    @ParameterizedTest
    @CsvSource({"1,0", "2,50", "3,200", "4,450"})
    void seuilDeChaqueNiveau(int niveau, int xp) {
        assertThat(LevelCalculator.xpForLevel(niveau)).isEqualTo(xp);
    }
}
