/*
 * Copyright (c) 2002-2020 "Neo4j,"
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
package org.neo4j.kernel.impl.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.NodeValue;
import org.neo4j.values.virtual.RelationshipValue;
import org.neo4j.values.virtual.VirtualValues;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.neo4j.values.storable.Values.stringValue;
import static org.neo4j.values.virtual.VirtualValues.EMPTY_MAP;
import static org.neo4j.values.virtual.VirtualValues.nodeValue;
import static org.neo4j.values.virtual.VirtualValues.path;

@ImpermanentDbmsExtension
class DefaultValueMapperTest
{
    @Inject
    private GraphDatabaseService db;

    @Test
    void shouldHandleSingleNodePath()
    {
        // Given
        Node node;
        try ( Transaction tx = db.beginTx() )
        {
            node = tx.createNode();
            tx.commit();
        }

        // Then
        try ( Transaction tx = db.beginTx() )
        {
            var mapper = new DefaultValueMapper( (InternalTransaction) tx );
            Path mapped = mapper.mapPath( path( asNodeValues( node ), asRelationshipsValues() ) );
            assertThat( mapped.length(), equalTo( 0 ) );
            assertThat( mapped.startNode(), equalTo( node ) );
            assertThat( mapped.endNode(), equalTo( node ) );
            assertThat( Iterables.asList( mapped.relationships() ), hasSize( 0 ) );
            assertThat( Iterables.asList( mapped.reverseRelationships() ), hasSize( 0 ) );
            assertThat( Iterables.asList( mapped.nodes() ), equalTo( singletonList( node ) ) );
            assertThat( Iterables.asList( mapped.reverseNodes() ), equalTo( singletonList( node ) ) );
            assertThat( mapped.lastRelationship(), nullValue() );
            assertThat( Iterators.asList( mapped.iterator() ), equalTo( singletonList( node ) ) );
        }
    }

    @Test
    void shouldHandleSingleRelationshipPath()
    {
        // Given
        Node start, end;
        Relationship relationship;
        try ( Transaction tx = db.beginTx() )
        {
            start = tx.createNode();
            end = tx.createNode();
            relationship = start.createRelationshipTo( end, RelationshipType.withName( "R" ) );
            tx.commit();
        }

        // Then
        try ( Transaction tx = db.beginTx() )
        {
            var mapper = new DefaultValueMapper( (InternalTransaction) tx );
            Path mapped = mapper.mapPath( path( asNodeValues( start, end ), asRelationshipsValues( relationship ) ) );
            assertThat( mapped.length(), equalTo( 1 ) );
            assertThat( mapped.startNode(), equalTo( start ) );
            assertThat( mapped.endNode(), equalTo( end ) );
            assertThat( Iterables.asList( mapped.relationships() ), equalTo( singletonList( relationship ) ) );
            assertThat( Iterables.asList( mapped.reverseRelationships() ), equalTo( singletonList( relationship ) ) );
            assertThat( Iterables.asList( mapped.nodes() ), equalTo( Arrays.asList( start, end ) ) );
            assertThat( Iterables.asList( mapped.reverseNodes() ), equalTo( Arrays.asList( end, start ) ) );
            assertThat( mapped.lastRelationship(), equalTo( relationship ) );
            assertThat( Iterators.asList( mapped.iterator() ), equalTo( Arrays.asList( start, relationship, end ) ) );
        }
    }

    @Test
    void shouldHandleLongPath()
    {
        // Given
        Node a, b, c, d, e;
        Relationship r1, r2, r3, r4;
        try ( Transaction tx = db.beginTx() )
        {
            a = tx.createNode();
            b = tx.createNode();
            c = tx.createNode();
            d = tx.createNode();
            e = tx.createNode();
            r1 = a.createRelationshipTo( b, RelationshipType.withName( "R" ) );
            r2 = b.createRelationshipTo( c, RelationshipType.withName( "R" ) );
            r3 = c.createRelationshipTo( d, RelationshipType.withName( "R" ) );
            r4 = d.createRelationshipTo( e, RelationshipType.withName( "R" ) );
            tx.commit();
        }

        // Then
        try ( Transaction tx = db.beginTx() )
        {
            var mapper = new DefaultValueMapper( (InternalTransaction) tx );
            Path mapped = mapper.mapPath( path( asNodeValues( a, b, c, d, e ), asRelationshipsValues( r1, r2, r3, r4 ) ) );
            assertThat( mapped.length(), equalTo( 4 ) );
            assertThat( mapped.startNode(), equalTo( a ) );
            assertThat( mapped.endNode(), equalTo( e ) );
            assertThat( Iterables.asList( mapped.relationships() ), equalTo( Arrays.asList( r1, r2, r3, r4 ) ) );
            assertThat( Iterables.asList( mapped.reverseRelationships() ), equalTo( Arrays.asList( r4, r3, r2, r1 ) ) );
            assertThat( Iterables.asList( mapped.nodes() ), equalTo( Arrays.asList( a, b, c, d, e ) ) );
            assertThat( Iterables.asList( mapped.reverseNodes() ), equalTo( Arrays.asList( e, d, c, b, a ) ) );
            assertThat( mapped.lastRelationship(), equalTo( r4 ) );
            assertThat( Iterators.asList( mapped.iterator() ),
                    equalTo( Arrays.asList( a, r1, b, r2, c, r3, d, r4, e ) ) );
        }
    }

    @Test
    void shouldMapDirectRelationship()
    {
        // Given
        Node start, end;
        Relationship relationship;
        try ( Transaction tx = db.beginTx() )
        {
            start = tx.createNode();
            end = tx.createNode();
            relationship = start.createRelationshipTo( end, RelationshipType.withName( "R" ) );
            tx.commit();
        }
        RelationshipValue relationshipValue =
                VirtualValues.relationshipValue( relationship.getId(), nodeValue( start.getId(),
                        Values.EMPTY_TEXT_ARRAY, EMPTY_MAP ), nodeValue( start.getId(),
                        Values.EMPTY_TEXT_ARRAY, EMPTY_MAP ), stringValue( "R" ), EMPTY_MAP );

        // Then
        try ( Transaction tx = db.beginTx() )
        {
            var mapper = new DefaultValueMapper( (InternalTransaction) tx );
            Relationship coreAPIRelationship = mapper.mapRelationship( relationshipValue );
            assertThat( coreAPIRelationship.getId(), equalTo( relationship.getId() ) );
            assertThat( coreAPIRelationship.getStartNode(), equalTo( start ) );
            assertThat( coreAPIRelationship.getEndNode(), equalTo( end ) );
        }
    }

    private NodeValue[] asNodeValues( Node... nodes )
    {
        return Arrays.stream( nodes ).map( ValueUtils::fromNodeEntity ).toArray( NodeValue[]::new );
    }

    private RelationshipValue[] asRelationshipsValues( Relationship... relationships )
    {
        return Arrays.stream( relationships ).map( ValueUtils::fromRelationshipEntity )
                .toArray( RelationshipValue[]::new );
    }
}
