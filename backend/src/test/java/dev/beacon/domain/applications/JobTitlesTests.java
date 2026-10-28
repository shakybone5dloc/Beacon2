package dev.beacon.domain.applications;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobTitlesTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Sr. Java Engineer        | senior java engineer",
            "  SENIOR   Java  Engineer | senior java engineer",
            "Jr Developer (Remote)    | junior developer remote",
            "C++ / C# Developer       | c++ c# developer",
            "Srinivas's Team Lead     | srinivas s team lead",
    })
    void normalizes(String title, String expected) {
        assertThat(JobTitles.normalize(title)).isEqualTo(expected);
    }
}
