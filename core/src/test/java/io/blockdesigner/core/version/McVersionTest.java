package io.blockdesigner.core.version;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McVersionTest {

    @Test
    void parsesLegacyAndModernVersionJson() throws Exception {
        ObjectMapper om = new ObjectMapper();
        McVersion old = McVersion.fromVersionJson(om.readTree("""
                {"id":"1.21.1","world_version":3955,"pack_version":{"resource":34,"data":48},"java_version":21}"""));
        assertThat(old).isEqualTo(new McVersion("1.21.1", 3955, 48, 0, 21));
        assertThat(old.structureFolder()).isEqualTo("structure");
        assertThat(old.usesMinMaxPackFormat()).isFalse();

        McVersion modern = McVersion.fromVersionJson(om.readTree("""
                {"id":"26.3","world_version":5023,"pack_version":{"resource_major":97,"resource_minor":1,"data_major":121,"data_minor":0},"java_version":25}"""));
        assertThat(modern.dataPackMajor()).isEqualTo(121);
        assertThat(modern.usesMinMaxPackFormat()).isTrue();
    }

    @Test
    void builtInTableAndLookup() {
        assertThat(McVersion.byId("1.20.1").orElseThrow().structureFolder()).isEqualTo("structures");
        assertThat(McVersion.forDataVersion(3960).id()).isEqualTo("1.21.1");
        assertThat(McVersion.latestKnown().dataVersion()).isGreaterThanOrEqualTo(5023);
    }
}
