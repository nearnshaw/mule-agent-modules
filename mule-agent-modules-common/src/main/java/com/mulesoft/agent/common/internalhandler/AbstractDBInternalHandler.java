package com.mulesoft.agent.common.internalhandler;

import com.mulesoft.agent.AgentEnableOperationException;
import com.mulesoft.agent.buffer.BufferedHandler;
import com.mulesoft.agent.configuration.Configurable;
import com.mulesoft.agent.configuration.Password;
import com.mulesoft.agent.handlers.exception.InitializationException;
import com.mulesoft.agent.services.OnOffSwitch;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;

public abstract class AbstractDBInternalHandler<T> extends BufferedHandler<T>
{
    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractDBInternalHandler.class);

    /**
     * <p>
     * Flag to identify if the Internal Handler is enabled or not.
     * Default: false
     * </p>
     */
    @Configurable("false")
    protected boolean enabled;

    /**
     * <p>
     * JDBC driver to use to communicate with the database server.
     * </p>
     */
    @Configurable
    public String driver;

    /**
     * <p>
     * JDBC URL to connect to the database server.
     * </p>
     */
    @Configurable
    public String jdbcUrl;

    /**
     * <p>
     * Username in the database server.
     * </p>
     */
    @Configurable
    public String user;

    /**
     * <p>
     * Password for the database user.
     * </p>
     */
    @Password
    @Configurable
    public String pass;

    protected abstract void insert(Connection connection, Collection<T> messages)
            throws SQLException;

    @Override
    protected boolean canHandle(T message)
    {
        return true;
    }

    @Override
    protected boolean flush(Collection<T> messages)
    {
        LOGGER.debug(String.format("Flushing %s messages.", messages.size()));

        Connection connection = null;
        try
        {
            connection = DriverManager.getConnection(this.jdbcUrl, this.user, this.pass);
            connection.setAutoCommit(false);
            try
            {
                insert(connection, messages);
                connection.commit();
            }
            catch (Exception ex)
            {
                LOGGER.error("There was an error inserting the messages. Rolling back the transaction.", ex);
                try
                {
                    connection.rollback();
                }
                catch (SQLException sqlEx)
                {
                    LOGGER.error("There was an error while rolling back the transaction.", sqlEx);
                }
            }

            return true;
        }
        catch (SQLException e)
        {
            LOGGER.error("Couldn't insert the tracking notifications.", e);
            return false;
        }
        finally
        {
            if (connection != null)
            {
                try
                {
                    connection.close();
                }
                catch (SQLException e)
                {
                    LOGGER.error("Error closing the database.", e);
                }
            }
        }
    }

    public void postConfigurable() throws AgentEnableOperationException
    {
        if (this.enabledSwitch == null)
        {
            this.enabledSwitch = OnOffSwitch.newNullSwitch(this.enabled);
        }
    }

    @Override
    public void initialize() throws InitializationException
    {
        super.initialize();
        LOGGER.debug("Configuring the Common DB Internal Handler...");

        if (StringUtils.isEmpty(this.driver)
                || StringUtils.isEmpty(this.jdbcUrl))
        {
            throw new InitializationException("Please review configuration; " +
                    "you must configure the following properties: driver and jdbcUrl.");
        }

        try
        {
            Class.forName(driver);
        }
        catch (ClassNotFoundException e)
        {
            throw new InitializationException(String.format("Couldn't load the database driver '%s'. " +
                    "Did you copy the JAR driver to the {MULE_HOME}/plugins/mule-agent-plugin/lib?", driver), e);
        }

        try
        {
            LOGGER.debug("Testing database connection...");
            DriverManager.getConnection(this.jdbcUrl, this.user, this.pass).close();
            LOGGER.debug("Database connection OK!.");
        }
        catch (SQLException e)
        {
            throw new InitializationException("There was an error on the connection to the DataBase. " +
                    "Please review your agent configuration.", e);
        }

        LOGGER.debug("Successfully configured the Common DB Internal Handler.");
    }
}
