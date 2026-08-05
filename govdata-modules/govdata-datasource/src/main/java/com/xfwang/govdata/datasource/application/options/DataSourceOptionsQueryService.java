package com.xfwang.govdata.datasource.application.options;

import com.xfwang.govdata.datasource.application.options.view.DataSourceOptionsView;
import com.xfwang.govdata.datasource.application.options.view.OptionView;
import com.xfwang.govdata.datasource.domain.enums.DataSourceEnums;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class DataSourceOptionsQueryService {

    public DataSourceOptionsView options() {
        return DataSourceOptionsView.builder()
            .environments(toViews(DataSourceEnums.Environment.values()))
            .accessModes(toViews(DataSourceEnums.AccessMode.values()))
            .classifications(toViews(DataSourceEnums.Classification.values()))
            .securityLevels(IntStream.rangeClosed(1, 5)
                .mapToObj(value -> OptionView.builder()
                    .value(String.valueOf(value)).label("安全等级 " + value).build())
                .toList())
            .statuses(toViews(DataSourceEnums.Status.values()))
            .testStatuses(toViews(DataSourceEnums.TestStatus.values()))
            .build();
    }

    private List<OptionView> toViews(DataSourceEnums.Coded[] values) {
        return Arrays.stream(values)
            .map(value -> OptionView.builder().value(value.code()).label(value.label()).build())
            .toList();
    }
}
