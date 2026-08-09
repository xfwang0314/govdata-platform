package com.xfwang.govdata.datasource.application.options;

import com.xfwang.govdata.datasource.application.options.view.DataSourceOptionsView;
import com.xfwang.govdata.datasource.application.options.view.OptionView;
import com.xfwang.govdata.datasource.domain.enums.DataSourceEnums;
import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 数据源常量与前端下拉字典选项查询应用服务。
 *
 * <p>负责将环境、访问模式、数据分类、安全等级、状态及测试原因码导出为统一的字典选项给前端下拉框消费。</p>
 */
@Service
public class DataSourceOptionsQueryService {

    /**
     * 查询数据源模块支持的所有枚举与下拉框选项。
     *
     * @return 选项汇总视图
     */
    public DataSourceOptionsView options() {
        return DataSourceOptionsView.builder()
            .environments(toViews(DataSourceEnums.Environment.values()))
            .accessModes(toViews(DataSourceEnums.AccessMode.values()))
            .classifications(toViews(DataSourceEnums.Classification.values()))
            .securityLevels(IntStream.rangeClosed(1, 5)
                .mapToObj(value -> OptionView.builder()
                    .value(String.valueOf(value))
                    .label("安全等级 " + value)
                    .build())
                .toList())
            .statuses(toViews(DataSourceEnums.Status.values()))
            .testStatuses(toViews(DataSourceEnums.TestStatus.values()))
            .connectionTestReasons(Arrays.stream(ConnectionTestReason.values())
                .map(value -> OptionView.builder().value(value.code()).label(value.message()).build())
                .toList())
            .build();
    }

    private List<OptionView> toViews(DataSourceEnums.Coded[] values) {
        return Arrays.stream(values)
            .map(value -> OptionView.builder().value(value.code()).label(value.label()).build())
            .toList();
    }
}
