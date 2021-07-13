/* Copyright 2021 KJP12
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package net.kjp12.hachimitsu.database.impl;// Created 2021-14-06T15:09:52

import net.kjp12.hachimitsu.database.api.DatabaseException;
import net.kjp12.hachimitsu.database.api.SqlConnectionProvider;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Intermediate statement handler class.
 *
 * @author KJP12
 * @since ${version}
 **/
public abstract class StatementHandler {
	protected final String statementRaw;
	protected final SqlConnectionProvider provider;
	protected PreparedStatement statement;

	protected StatementHandler(SqlConnectionProvider provider, String statementRaw) {
		this.provider = provider;
		this.statementRaw = statementRaw;
	}

	public abstract void query(Object i);

	public final Statement prepareStatement() throws DatabaseException {
		try {
			closeStatement();
			return statement = provider.getConnection().prepareStatement(statementRaw);
		} catch (SQLException sql) {
			throw new DatabaseException(sql, statement, statementRaw);
		}
	}

	public final void closeStatement() throws DatabaseException {
		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException sql) {
				throw new DatabaseException(sql, statement);
			} finally {
				statement = null;
			}
		}
	}
}
