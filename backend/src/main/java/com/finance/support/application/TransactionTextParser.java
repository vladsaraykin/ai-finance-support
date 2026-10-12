package com.finance.support.application;

import com.finance.support.api.TransactionDraft;
import com.finance.support.domain.Category;
import com.finance.support.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TransactionTextParser {
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\p{L}\\d])\\d{1,6}(?:[ \\u00a0]\\d{3})*(?:[,.]\\d{1,2})?(?!\\d)");
    private static final Pattern MERCHANT = Pattern.compile("(?:на|в)\\s+([^,.;]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRAILING_FILLER = Pattern.compile("\\s+(?:и|а также|затем)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final OwnerProperties properties;

    public TransactionTextParser(OwnerProperties properties) {
        this.properties = properties;
    }

    public List<TransactionDraft> parse(String input) {
        String text = input.strip();
        Matcher matcher = AMOUNT.matcher(text);
        List<Match> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new Match(matcher.start(), matcher.end(), matcher.group()));
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Не нашёл сумму. Пример: «Вчера потратил 800 рублей на такси».");
        }
        if (matches.size() > 20) {
            throw new IllegalArgumentException("За одно сообщение можно добавить не больше 20 операций.");
        }

        TransactionType type = isIncome(text) ? TransactionType.INCOME : TransactionType.EXPENSE;
        LocalDate date = transactionDate(text);
        OffsetDateTime occurredAt = date.atTime(LocalTime.NOON).atZone(properties.timezone()).toOffsetDateTime();
        List<TransactionDraft> drafts = new ArrayList<>();

        for (int index = 0; index < matches.size(); index++) {
            Match amountMatch = matches.get(index);
            int segmentEnd = index + 1 < matches.size() ? matches.get(index + 1).start() : text.length();
            String context = text.substring(amountMatch.start(), segmentEnd);
            String detail = text.substring(amountMatch.end(), segmentEnd).strip();
            String amountText = amountMatch.amount().replace(" ", "").replace("\u00a0", "").replace(',', '.');
            BigDecimal amount = new BigDecimal(amountText).setScale(2);

            Category category = categoryFor((context + " " + detail).toLowerCase(Locale.forLanguageTag("ru")), type);
            String merchant = merchantFrom(detail);
            String description = detail.replaceAll("(?i)руб(?:лей|ля|ль)?\\.?", "").strip();
            if (description.isBlank()) {
                description = category.name();
            }
            drafts.add(new TransactionDraft(type, amount, "RUB", occurredAt, category, merchant, description));
        }
        return List.copyOf(drafts);
    }

    private LocalDate transactionDate(String text) {
        String normalized = text.toLowerCase(Locale.forLanguageTag("ru"));
        LocalDate today = LocalDate.now(properties.timezone());
        if (normalized.contains("вчера")) return today.minusDays(1);
        if (normalized.contains("сегодня")) return today;
        return today;
    }

    private boolean isIncome(String text) {
        String normalized = text.toLowerCase(Locale.forLanguageTag("ru"));
        return normalized.contains("получил") || normalized.contains("получила")
                || normalized.contains("зарплат") || normalized.contains("доход")
                || normalized.contains("поступил");
    }

    private Category categoryFor(String text, TransactionType type) {
        if (type == TransactionType.INCOME) return Category.SALARY;
        if (hasAny(text, "продукт", "перекрёст", "перекрест", "пятёроч", "пятероч", "магазин", "супермаркет")) return Category.GROCERIES;
        if (hasAny(text, "обед", "ужин", "завтрак", "кафе", "ресторан", "кофе")) return Category.RESTAURANTS;
        if (hasAny(text, "такси", "метро", "автобус", "транспорт", "бензин", "бенз", "парков")) return Category.TRANSPORT;
        if (hasAny(text, "аренд", "ипотек", "жиль")) return Category.HOUSING;
        if (hasAny(text, "аптек", "врач", "лечен", "медицин")) return Category.HEALTH;
        if (hasAny(text, "кино", "игр", "театр", "развлеч")) return Category.ENTERTAINMENT;
        if (hasAny(text, "коммун", "свет", "вода", "интернет", "телефон")) return Category.UTILITIES;
        return Category.OTHER;
    }

    private String merchantFrom(String detail) {
        Matcher matcher = MERCHANT.matcher(detail);
        if (!matcher.find()) return null;
        String value = matcher.group(1).strip();
        value = TRAILING_FILLER.matcher(value).replaceFirst("").strip();
        value = value.replaceAll("(?i)руб(?:лей|ля|ль)?\\.?$", "").strip();
        return value.isBlank() ? null : value.substring(0, Math.min(value.length(), 160));
    }

    private boolean hasAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private record Match(int start, int end, String amount) {}
}
