package org.dromara.common.core.utils;

import cn.hutool.core.date.DateUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.core.enums.FormatsType;
import org.dromara.common.core.exception.ServiceException;

import java.lang.management.ManagementFactory;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;

/**
 * 时间工具类。
 *
 * @author ruoyi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateUtils {

    public static LocalDateTime getNowDate() {
        return LocalDateTime.now();
    }

    public static String getDate() {
        return dateTimeNow(FormatsType.YYYY_MM_DD);
    }

    public static String getCurrentDate() {
        return dateTimeNow(FormatsType.YYYYMMDD);
    }

    public static String datePath() {
        return dateTimeNow(FormatsType.YYYY_MM_DD_SLASH);
    }

    public static String getTime() {
        return dateTimeNow(FormatsType.YYYY_MM_DD_HH_MM_SS);
    }

    public static String getTimeWithHourMinuteSecond() {
        return dateTimeNow(FormatsType.HH_MM_SS);
    }

    public static String dateTimeNow() {
        return dateTimeNow(FormatsType.YYYYMMDDHHMMSS);
    }

    public static String dateTimeNow(FormatsType format) {
        return LocalDateTime.now().format(formatter(format));
    }

    public static String formatDate(LocalDateTime date) {
        return parseDateToStr(FormatsType.YYYY_MM_DD, date);
    }

    public static String formatDateTime(LocalDateTime date) {
        return parseDateToStr(FormatsType.YYYY_MM_DD_HH_MM_SS, date);
    }

    public static String formatDateTime(Object date) {
        return parseDateToStr(FormatsType.YYYY_MM_DD_HH_MM_SS, date);
    }

    public static String parseDateToStr(FormatsType format, LocalDateTime date) {
        return date == null ? null : date.format(formatter(format));
    }

    /**
     * 兼容第三方组件仍返回旧时间对象的边界场景，平台领域模型不再使用 Date。
     */
    public static String parseDateToStr(FormatsType format, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return parseDateToStr(format, localDateTime);
        }
        if (value instanceof LocalDate localDate) {
            return parseDateToStr(format, localDate.atStartOfDay());
        }
        if (value instanceof java.util.Date legacyDate) {
            return DateUtil.format(legacyDate, format.getTimeFormat());
        }
        return DateUtil.format(DateUtil.parse(value.toString()), format.getTimeFormat());
    }

    public static LocalDateTime parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        try {
            return DateUtil.parse(value.toString()).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static LocalDateTime getServerStartDate() {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
    }

    public static long difference(LocalDateTime start, LocalDateTime end, TimeUnit unit) {
        long millis = Math.abs(Duration.between(start, end).toMillis());
        return switch (unit) {
            case DAYS -> TimeUnit.MILLISECONDS.toDays(millis);
            case HOURS -> TimeUnit.MILLISECONDS.toHours(millis);
            case MINUTES -> TimeUnit.MILLISECONDS.toMinutes(millis);
            case SECONDS -> TimeUnit.MILLISECONDS.toSeconds(millis);
            case MILLISECONDS -> millis;
            case MICROSECONDS -> TimeUnit.MILLISECONDS.toMicros(millis);
            case NANOSECONDS -> TimeUnit.MILLISECONDS.toNanos(millis);
        };
    }

    public static String getDatePoor(LocalDateTime endDate, LocalDateTime nowDate) {
        long millis = Duration.between(nowDate, endDate).toMillis();
        long day = TimeUnit.MILLISECONDS.toDays(millis);
        long hour = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long min = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return String.format("%d天 %d小时 %d分钟", day, hour, min);
    }

    public static String getTimeDifference(LocalDateTime endDate, LocalDateTime nowDate) {
        long seconds = Math.max(0, Duration.between(nowDate, endDate).getSeconds());
        long day = seconds / 86_400;
        long hour = seconds / 3_600 % 24;
        long min = seconds / 60 % 60;
        long sec = seconds % 60;
        StringBuilder result = new StringBuilder();
        if (day > 0) {
            result.append(day).append("天 ");
        }
        if (hour > 0) {
            result.append(hour).append("小时 ");
        }
        if (min > 0) {
            result.append(min).append("分钟 ");
        }
        if (sec > 0) {
            result.append(sec).append("秒");
        }
        return result.length() > 0 ? result.toString().trim() : "0秒";
    }

    public static String getTimeDifference(Object endDate, Object nowDate) {
        return getTimeDifference(toLocalDateTime(endDate), toLocalDateTime(nowDate));
    }

    public static void validateDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                         int maxValue, TimeUnit unit) {
        if (endDate.isBefore(startDate)) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        long diff = switch (unit) {
            case DAYS -> ChronoUnit.DAYS.between(startDate, endDate);
            case HOURS -> ChronoUnit.HOURS.between(startDate, endDate);
            case MINUTES -> ChronoUnit.MINUTES.between(startDate, endDate);
            default -> throw new IllegalArgumentException("不支持的时间单位：" + unit.name());
        };
        if (diff > maxValue) {
            throw new ServiceException("最大时间跨度为 " + maxValue + " " + unit.name().toLowerCase());
        }
    }

    public static String getTodayHour(LocalDateTime date) {
        int hour = date.getHour();
        if (hour <= 6) {
            return "凌晨";
        } else if (hour < 12) {
            return "上午";
        } else if (hour == 12) {
            return "中午";
        } else if (hour <= 18) {
            return "下午";
        }
        return "晚上";
    }

    public static String formatFriendlyTime(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        if (date.isAfter(now) || date.getYear() != now.getYear()) {
            return date.format(formatter(FormatsType.YYYY_MM_DD_HH_MM));
        }
        if (date.toLocalDate().isEqual(now.toLocalDate())) {
            long minutes = ChronoUnit.MINUTES.between(date, now);
            if (minutes < 1) {
                return "刚刚";
            }
            if (minutes < 60) {
                return minutes + "分钟前";
            }
            return getTodayHour(date) + " " + date.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (date.toLocalDate().isEqual(now.toLocalDate().minusDays(1))) {
            return "昨天 " + date.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        LocalDate weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!date.toLocalDate().isBefore(weekStart)) {
            String[] weekNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            return weekNames[date.getDayOfWeek().getValue() - 1] + " "
                + date.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return date.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    private static DateTimeFormatter formatter(FormatsType format) {
        return DateTimeFormatter.ofPattern(format.getTimeFormat());
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (value instanceof java.util.Date legacyDate) {
            return LocalDateTime.ofInstant(legacyDate.toInstant(), ZoneId.systemDefault());
        }
        LocalDateTime parsed = parseDate(value);
        if (parsed == null) {
            throw new IllegalArgumentException("无法解析时间：" + value);
        }
        return parsed;
    }
}
