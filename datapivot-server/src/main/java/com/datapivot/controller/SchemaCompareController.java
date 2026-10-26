package com.datapivot.controller;

import com.datapivot.common.R;
import com.datapivot.dto.CompareDTO;
import com.datapivot.service.SchemaCompareService;
import com.datapivot.vo.CompareResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 数据库结构对比接口
 */
@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class SchemaCompareController {

    /** 下载文件名白名单：字母数字下划线点横线，防路径穿越与响应头注入 */
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z0-9_\\-.]{1,100}$");

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final SchemaCompareService compareService;

    /** 执行结构对比（表级 + 字段级差异 + DDL 脚本） */
    @PostMapping("/execute")
    public R<CompareResultVO> execute(@Valid @RequestBody CompareDTO dto) {
        return R.ok(compareService.compare(dto.getBaseConnId(), dto.getTargetConnId()));
    }

    /**
     * 下载 DDL 升级脚本（.sql）。
     * <p>安全说明：文件名仅允许白名单字符（默认服务端生成），杜绝路径穿越；
     * 脚本内容重新按连接实时生成，不落盘、不缓存。</p>
     */
    @PostMapping("/download")
    public ResponseEntity<Resource> download(@Valid @RequestBody CompareDTO dto) {
        CompareResultVO result = compareService.compare(dto.getBaseConnId(), dto.getTargetConnId());
        String fileName = result.getTargetConnName() + "_升级脚本_" + LocalDateTime.now().format(FILE_TIME) + ".sql";
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            // 连接名含特殊字符时退化为安全默认名
            fileName = "datapivot_upgrade_" + LocalDateTime.now().format(FILE_TIME) + ".sql";
        }
        byte[] bytes = result.getDdlScript().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "sql", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).contentLength(bytes.length).body(new ByteArrayResource(bytes));
    }
}
