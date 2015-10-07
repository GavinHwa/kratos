/*
 * Copyright 1999-2101 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gxl.kratos.sql.dialect.mysql.visitor;

import java.util.ArrayList;
import java.util.List;

import com.gxl.kratos.sql.ast.SQLOrderBy;
import com.gxl.kratos.sql.ast.expr.SQLBetweenExpr;
import com.gxl.kratos.sql.ast.expr.SQLBinaryOpExpr;
import com.gxl.kratos.sql.ast.expr.SQLInListExpr;
import com.gxl.kratos.sql.ast.expr.SQLMethodInvokeExpr;
import com.gxl.kratos.sql.ast.statement.SQLSelectGroupByClause;
import com.gxl.kratos.sql.ast.statement.SQLSelectItem;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock.Limit;
import com.gxl.kratos.sql.visitor.ExportParameterVisitor;
import com.gxl.kratos.sql.visitor.ExportParameterVisitorUtils;

public class MySqlExportParameterVisitor extends MySqlASTVisitorAdapter implements ExportParameterVisitor {

    private final List<Object> parameters;
    
    public MySqlExportParameterVisitor() {
        this(new ArrayList<Object>());
    }

    public MySqlExportParameterVisitor(List<Object> parameters){
        this.parameters = parameters;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    @Override
    public boolean visit(SQLSelectItem x) {
        return false;
    }

    @Override
    public boolean visit(Limit x) {
        return false;
    }

    @Override
    public boolean visit(SQLOrderBy x) {
        return false;
    }

    @Override
    public boolean visit(SQLSelectGroupByClause x) {
        return false;
    }

    @Override
    public boolean visit(SQLMethodInvokeExpr x) {
        ExportParameterVisitorUtils.exportParamterAndAccept(this.parameters, x.getParameters());

        return true;
    }

    @Override
    public boolean visit(SQLInListExpr x) {
        ExportParameterVisitorUtils.exportParamterAndAccept(this.parameters, x.getTargetList());

        return true;
    }

    @Override
    public boolean visit(SQLBetweenExpr x) {
        ExportParameterVisitorUtils.exportParameter(this.parameters, x);
        return true;
    }

    public boolean visit(SQLBinaryOpExpr x) {
        ExportParameterVisitorUtils.exportParameter(this.parameters, x);
        return true;
    }

}
