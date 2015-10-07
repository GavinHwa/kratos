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
package com.gxl.kratos.sql.dialect.mysql.parser;

import com.gxl.kratos.sql.ast.SQLExpr;
import com.gxl.kratos.sql.ast.SQLSetQuantifier;
import com.gxl.kratos.sql.ast.expr.SQLIdentifierExpr;
import com.gxl.kratos.sql.ast.expr.SQLLiteralExpr;
import com.gxl.kratos.sql.ast.statement.SQLExprTableSource;
import com.gxl.kratos.sql.ast.statement.SQLSelect;
import com.gxl.kratos.sql.ast.statement.SQLSelectGroupByClause;
import com.gxl.kratos.sql.ast.statement.SQLSelectQuery;
import com.gxl.kratos.sql.ast.statement.SQLSelectQueryBlock;
import com.gxl.kratos.sql.ast.statement.SQLSubqueryTableSource;
import com.gxl.kratos.sql.ast.statement.SQLTableSource;
import com.gxl.kratos.sql.ast.statement.SQLUnionQuery;
import com.gxl.kratos.sql.ast.statement.SQLUnionQueryTableSource;
import com.gxl.kratos.sql.ast.statement.SQLUpdateSetItem;
import com.gxl.kratos.sql.dialect.mysql.ast.MySqlForceIndexHint;
import com.gxl.kratos.sql.dialect.mysql.ast.MySqlIgnoreIndexHint;
import com.gxl.kratos.sql.dialect.mysql.ast.MySqlIndexHint;
import com.gxl.kratos.sql.dialect.mysql.ast.MySqlIndexHintImpl;
import com.gxl.kratos.sql.dialect.mysql.ast.MySqlUseIndexHint;
import com.gxl.kratos.sql.dialect.mysql.ast.expr.MySqlOutFileExpr;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlSelectGroupBy;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlUnionQuery;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlUpdateTableSource;
import com.gxl.kratos.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock.Limit;
import com.gxl.kratos.sql.parser.ParserException;
import com.gxl.kratos.sql.parser.SQLExprParser;
import com.gxl.kratos.sql.parser.SQLSelectParser;
import com.gxl.kratos.sql.parser.Token;

public class MySqlSelectParser extends SQLSelectParser {

    public MySqlSelectParser(SQLExprParser exprParser){
        super(exprParser);
    }

    public MySqlSelectParser(String sql){
        this(new MySqlExprParser(sql));
    }

    @Override
    public SQLSelectQuery query() {
        if (lexer.token() == (Token.LPAREN)) {
            lexer.nextToken();

            SQLSelectQuery select = query();
            accept(Token.RPAREN);

            return queryRest(select);
        }

        MySqlSelectQueryBlock queryBlock = new MySqlSelectQueryBlock();

        if (lexer.token() == Token.SELECT) {
            lexer.nextToken();

            if (lexer.token() == Token.HINT) {
                this.exprParser.parseHints(queryBlock.getHints());
            }

            if (lexer.token() == Token.COMMENT) {
                lexer.nextToken();
            }

            if (lexer.token() == (Token.DISTINCT)) {
                queryBlock.setDistionOption(SQLSetQuantifier.DISTINCT);
                lexer.nextToken();
            } else if (identifierEquals("DISTINCTROW")) {
                queryBlock.setDistionOption(SQLSetQuantifier.DISTINCTROW);
                lexer.nextToken();
            } else if (lexer.token() == (Token.ALL)) {
                queryBlock.setDistionOption(SQLSetQuantifier.ALL);
                lexer.nextToken();
            }

            if (identifierEquals("HIGH_PRIORITY")) {
                queryBlock.setHignPriority(true);
                lexer.nextToken();
            }

            if (identifierEquals("STRAIGHT_JOIN")) {
                queryBlock.setStraightJoin(true);
                lexer.nextToken();
            }

            if (identifierEquals("SQL_SMALL_RESULT")) {
                queryBlock.setSmallResult(true);
                lexer.nextToken();
            }

            if (identifierEquals("SQL_BIG_RESULT")) {
                queryBlock.setBigResult(true);
                lexer.nextToken();
            }

            if (identifierEquals("SQL_BUFFER_RESULT")) {
                queryBlock.setBufferResult(true);
                lexer.nextToken();
            }

            if (identifierEquals("SQL_CACHE")) {
                queryBlock.setCache(true);
                lexer.nextToken();
            }

            if (identifierEquals("SQL_NO_CACHE")) {
                queryBlock.setCache(false);
                lexer.nextToken();
            }

            if (identifierEquals("SQL_CALC_FOUND_ROWS")) {
                queryBlock.setCalcFoundRows(true);
                lexer.nextToken();
            }

            parseSelectList(queryBlock);
            
            parseInto(queryBlock);
        }

        parseFrom(queryBlock);

        parseWhere(queryBlock);

        parseGroupBy(queryBlock);

        queryBlock.setOrderBy(this.exprParser.parseOrderBy());

        if (lexer.token() == Token.LIMIT) {
            queryBlock.setLimit(parseLimit());
        }

        if (lexer.token() == Token.PROCEDURE) {
            lexer.nextToken();
            throw new ParserException("TODO");
        }

        parseInto(queryBlock);

        if (lexer.token() == Token.FOR) {
            lexer.nextToken();
            accept(Token.UPDATE);

            queryBlock.setForUpdate(true);
        }

        if (lexer.token() == Token.LOCK) {
            lexer.nextToken();
            accept(Token.IN);
            acceptIdentifier("SHARE");
            acceptIdentifier("MODE");
            queryBlock.setLockInShareMode(true);
        }

        return queryRest(queryBlock);
    }
    
    public SQLTableSource parseTableSource() {
        if (lexer.token() == Token.LPAREN) {
            lexer.nextToken();
            SQLTableSource tableSource;
            if (lexer.token() == Token.SELECT || lexer.token() == Token.WITH) {
                SQLSelect select = select();
                accept(Token.RPAREN);
                SQLSelectQuery query = queryRest(select.getQuery());
                if (query instanceof SQLUnionQuery) {
                    tableSource = new SQLUnionQueryTableSource((SQLUnionQuery) query);
                } else {
                    tableSource = new SQLSubqueryTableSource(select);
                }
            } else if (lexer.token() == Token.LPAREN) {
                tableSource = parseTableSource();
                accept(Token.RPAREN);
            } else {
                tableSource = parseTableSource();
                accept(Token.RPAREN);
            }

            return parseTableSourceRest(tableSource);
        }
        
        if(lexer.token() == Token.UPDATE) {
            SQLTableSource tableSource = new MySqlUpdateTableSource(parseUpdateStatment());
            return parseTableSourceRest(tableSource);
        }

        if (lexer.token() == Token.SELECT) {
            throw new ParserException("TODO");
        }

        SQLExprTableSource tableReference = new SQLExprTableSource();

        parseTableSourceQueryTableExpr(tableReference);

        SQLTableSource tableSrc = parseTableSourceRest(tableReference);
        
        if (lexer.hasComment() && lexer.isKeepComments()) {
            tableSrc.addAfterComment(lexer.readAndResetComments());
        }
        
        return tableSrc;
    }
    
    private MySqlUpdateStatement parseUpdateStatment() {
        MySqlUpdateStatement update = new MySqlUpdateStatement();

        lexer.nextToken();

        if (identifierEquals("LOW_PRIORITY")) {
            lexer.nextToken();
            update.setLowPriority(true);
        }

        if (identifierEquals("IGNORE")) {
            lexer.nextToken();
            update.setIgnore(true);
        }

        SQLTableSource updateTableSource = this.exprParser.createSelectParser().parseTableSource();
        update.setTableSource(updateTableSource);

        accept(Token.SET);

        for (;;) {
            SQLUpdateSetItem item = this.exprParser.parseUpdateSetItem();
            update.addItem(item);

            if (lexer.token() != Token.COMMA) {
                break;
            }

            lexer.nextToken();
        }

        if (lexer.token() == (Token.WHERE)) {
            lexer.nextToken();
            update.setWhere(this.exprParser.expr());
        }

        update.setOrderBy(this.exprParser.parseOrderBy());
        update.setLimit(parseLimit());
        
        return update;
    }
    
    protected void parseInto(SQLSelectQueryBlock queryBlock) {
        if (lexer.token() == (Token.INTO)) {
            lexer.nextToken();

            if (identifierEquals("OUTFILE")) {
                lexer.nextToken();

                MySqlOutFileExpr outFile = new MySqlOutFileExpr();
                outFile.setFile(expr());

                queryBlock.setInto(outFile);

                if (identifierEquals("FIELDS") || identifierEquals("COLUMNS")) {
                    lexer.nextToken();

                    if (identifierEquals("TERMINATED")) {
                        lexer.nextToken();
                        accept(Token.BY);
                    }
                    outFile.setColumnsTerminatedBy((SQLLiteralExpr) expr());

                    if (identifierEquals("OPTIONALLY")) {
                        lexer.nextToken();
                        outFile.setColumnsEnclosedOptionally(true);
                    }

                    if (identifierEquals("ENCLOSED")) {
                        lexer.nextToken();
                        accept(Token.BY);
                        outFile.setColumnsEnclosedBy((SQLLiteralExpr) expr());
                    }

                    if (identifierEquals("ESCAPED")) {
                        lexer.nextToken();
                        accept(Token.BY);
                        outFile.setColumnsEscaped((SQLLiteralExpr) expr());
                    }
                }

                if (identifierEquals("LINES")) {
                    lexer.nextToken();

                    if (identifierEquals("STARTING")) {
                        lexer.nextToken();
                        accept(Token.BY);
                        outFile.setLinesStartingBy((SQLLiteralExpr) expr());
                    } else {
                        identifierEquals("TERMINATED");
                        lexer.nextToken();
                        accept(Token.BY);
                        outFile.setLinesTerminatedBy((SQLLiteralExpr) expr());
                    }
                }
            } else {
                queryBlock.setInto(this.exprParser.name());
            }
        }
    }

    protected void parseGroupBy(SQLSelectQueryBlock queryBlock) {
        SQLSelectGroupByClause groupBy = null;

        if (lexer.token() == Token.GROUP) {
            groupBy = new SQLSelectGroupByClause();

            lexer.nextToken();
            accept(Token.BY);

            while (true) {
                groupBy.addItem(this.getExprParser().parseSelectGroupByItem());
                if (!(lexer.token() == (Token.COMMA))) {
                    break;
                }
                lexer.nextToken();
            }

            if (lexer.token() == Token.WITH) {
                lexer.nextToken();
                acceptIdentifier("ROLLUP");

                MySqlSelectGroupBy mySqlGroupBy = new MySqlSelectGroupBy();
                for (SQLExpr sqlExpr : groupBy.getItems()) {
                    mySqlGroupBy.addItem(sqlExpr);
                }
                mySqlGroupBy.setRollUp(true);

                groupBy = mySqlGroupBy;
            }
        }

        if (lexer.token() == Token.HAVING) {
            lexer.nextToken();

            if (groupBy == null) {
                groupBy = new SQLSelectGroupByClause();
            }
            groupBy.setHaving(this.exprParser.expr());
        }

        queryBlock.setGroupBy(groupBy);
    }

    protected SQLTableSource parseTableSourceRest(SQLTableSource tableSource) {
        if (identifierEquals("USING")) {
            return tableSource;
        }

        if (lexer.token() == Token.USE) {
            lexer.nextToken();
            MySqlUseIndexHint hint = new MySqlUseIndexHint();
            parseIndexHint(hint);
            tableSource.getHints().add(hint);
        }

        if (identifierEquals("IGNORE")) {
            lexer.nextToken();
            MySqlIgnoreIndexHint hint = new MySqlIgnoreIndexHint();
            parseIndexHint(hint);
            tableSource.getHints().add(hint);
        }

        if (identifierEquals("FORCE")) {
            lexer.nextToken();
            MySqlForceIndexHint hint = new MySqlForceIndexHint();
            parseIndexHint(hint);
            tableSource.getHints().add(hint);
        }

        return super.parseTableSourceRest(tableSource);
    }

    private void parseIndexHint(MySqlIndexHintImpl hint) {
        if (lexer.token() == Token.INDEX) {
            lexer.nextToken();
        } else {
            accept(Token.KEY);
        }

        if (lexer.token() == Token.FOR) {
            lexer.nextToken();

            if (lexer.token() == Token.JOIN) {
                lexer.nextToken();
                hint.setOption(MySqlIndexHint.Option.JOIN);
            } else if (lexer.token() == Token.ORDER) {
                lexer.nextToken();
                accept(Token.BY);
                hint.setOption(MySqlIndexHint.Option.ORDER_BY);
            } else {
                accept(Token.GROUP);
                accept(Token.BY);
                hint.setOption(MySqlIndexHint.Option.GROUP_BY);
            }
        }

        accept(Token.LPAREN);
        if (lexer.token() == Token.PRIMARY) {
            lexer.nextToken();
            hint.getIndexList().add(new SQLIdentifierExpr("PRIMARY"));
        } else {
            this.exprParser.names(hint.getIndexList());
        }
        accept(Token.RPAREN);
    }

    protected MySqlUnionQuery createSQLUnionQuery() {
        return new MySqlUnionQuery();
    }

    public SQLUnionQuery unionRest(SQLUnionQuery union) {
        if (lexer.token() == Token.LIMIT) {
            MySqlUnionQuery mysqlUnionQuery = (MySqlUnionQuery) union;
            mysqlUnionQuery.setLimit(parseLimit());
        }
        return super.unionRest(union);
    }

    public Limit parseLimit() {
        return ((MySqlExprParser) this.exprParser).parseLimit();
    }
    
    public MySqlExprParser getExprParser() {
        return (MySqlExprParser) exprParser;
    }
}