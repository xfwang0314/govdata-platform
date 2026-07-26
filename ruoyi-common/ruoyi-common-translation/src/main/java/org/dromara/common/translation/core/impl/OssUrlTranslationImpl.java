package org.dromara.common.translation.core.impl;

import cn.hutool.core.convert.Convert;
import org.dromara.system.api.domain.OssDTO;
import org.dromara.system.api.OssService;
import org.dromara.common.translation.annotation.TranslationType;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.common.translation.core.TranslationInterface;
import lombok.AllArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OSS翻译实现
 *
 * @author Lion Li
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.OSS_ID_TO_URL)
public class OssUrlTranslationImpl implements TranslationInterface<String> {

    private final OssService ossService;

    @Override
    public String translation(Object key, String other) {
        if (key instanceof String ids) {
            return ossService.selectUrlByIds(ids);
        } else if (key instanceof Long id) {
            return ossService.selectUrlByIds(id.toString());
        }
        return null;
    }

    @Override
    public Map<Object, String> translationBatch(Set<Object> keys, String other) {
        Set<Long> ids = collectLongIds(keys);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String idText = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, String> urls = new LinkedHashMap<>();
        for (OssDTO oss : ossService.selectByIds(idText)) {
            urls.put(oss.getOssId(), oss.getUrl());
        }
        Map<Object, String> result = new LinkedHashMap<>(keys.size());
        for (Object key : keys) {
            result.put(key, key instanceof String text
                ? joinMappedValues(text, urls::get) : urls.get(Convert.toLong(key)));
        }
        return result;
    }
}
