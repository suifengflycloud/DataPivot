package com.datapivot.controller;

import com.datapivot.common.R;
import com.datapivot.entity.DbConnection;
import com.datapivot.service.DbConnectionService;
import com.datapivot.service.MetadataService;
import com.datapivot.vo.ColumnMetaVO;
import com.datapivot.vo.TableMetaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据查询接口：全部走 JDBC DatabaseMetaData
 */
@Validated
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetadataController {

    private final DbConnectionService connectionService;
    private final MetadataService metadataService;

    /** 拉取连接下全部表（表名 + 表注释） */
    @GetMapping("/tables")
    public R<List<TableMetaVO>> tables(@RequestParam Long connectionId) {
        DbConnection conn = connectionService.getByIdNotNull(connectionId);
        return R.ok(metadataService.listTables(conn));
    }

    /** 拉取某表全部字段详情 */
    @GetMapping("/columns")
    public R<List<ColumnMetaVO>> columns(@RequestParam Long connectionId,
                                         @RequestParam String tableName,
                                         @RequestParam(required = false) String catalog,
                                         @RequestParam(required = false) String schema) {
        DbConnection conn = connectionService.getByIdNotNull(connectionId);
        return R.ok(metadataService.listColumns(conn, catalog, schema, tableName));
    }
}
