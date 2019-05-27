/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.v1.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;

import org.neo4j.bolt.transport.NettyServer;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.internal.helpers.ListenSocketAddress;
import org.neo4j.internal.helpers.NamedThreadFactory;
import org.neo4j.internal.helpers.PortBindException;
import org.neo4j.logging.NullLog;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NettyServerTest
{
    private NettyServer server;

    @AfterEach
    void afterEach() throws Exception
    {
        if ( server != null )
        {
            server.stop();
        }
    }

    @Test
    void shouldGivePortConflictErrorWithPortNumberInIt() throws Throwable
    {
        // Given an occupied port
        var port = 16000;
        try ( ServerSocketChannel ignore = ServerSocketChannel.open().bind( new InetSocketAddress( "localhost", port ) ) )
        {
            var address = new ListenSocketAddress( "localhost", port );

            // When
            var initializersMap = Map.of( new BoltConnector( "test" ), protocolOnAddress( address ) );
            server = new NettyServer( newThreadFactory(), initializersMap, new ConnectorPortRegister(), NullLog.getInstance() );

            // Then
            assertThrows( PortBindException.class, server::start );
        }
    }

    @Test
    void shouldRegisterPortInPortRegister() throws Exception
    {
        var connector = "bolt";
        var portRegister = new ConnectorPortRegister();

        var address = new ListenSocketAddress( "localhost", 0 );
        var initializersMap = Map.of( new BoltConnector( connector ), protocolOnAddress( address ) );
        server = new NettyServer( newThreadFactory(), initializersMap, portRegister, NullLog.getInstance() );

        assertNull( portRegister.getLocalAddress( connector ) );

        server.start();
        var actualAddress = portRegister.getLocalAddress( connector );
        assertNotNull( actualAddress );
        assertThat( actualAddress.getPort(), greaterThan( 0 ) );

        server.stop();
        assertNull( portRegister.getLocalAddress( connector ) );
    }

    private static NettyServer.ProtocolInitializer protocolOnAddress( ListenSocketAddress address )
    {
        return new NettyServer.ProtocolInitializer()
        {
            @Override
            public ChannelInitializer<Channel> channelInitializer()
            {
                return new ChannelInitializer<>()
                {
                    @Override
                    public void initChannel( Channel ch )
                    {
                    }
                };
            }

            @Override
            public ListenSocketAddress address()
            {
                return address;
            }
        };
    }

    private static NamedThreadFactory newThreadFactory()
    {
        return new NamedThreadFactory( "test-threads", true );
    }
}
