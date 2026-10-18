package com.finance.support.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CategoryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void predefinedCategoriesKeepStringJsonContract() throws Exception {
        assertThat(mapper.writeValueAsString(Category.GROCERIES)).isEqualTo("\"GROCERIES\"");
        assertThat(mapper.readValue("\"GROCERIES\"", Category.class)).isEqualTo(Category.GROCERIES);
    }

    @Test
    void importedUserCategoriesRoundTripAsStrings() throws Exception {
        var category = Category.valueOf("Теннис");
        assertThat(mapper.readValue(mapper.writeValueAsString(category), Category.class)).isEqualTo(category);
        assertThat(category.name()).isEqualTo("Теннис");
    }

    @Test
    void invalidCategoryNamesAreRejected() {
        assertThatThrownBy(() -> Category.valueOf(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.valueOf("a".repeat(161))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.valueOf("bad\ncategory")).isInstanceOf(IllegalArgumentException.class);
    }
}
