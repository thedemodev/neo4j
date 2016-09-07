/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.recovery;

import java.io.IOException;

import org.neo4j.helpers.collection.Visitor;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.IOLimiter;
import org.neo4j.kernel.impl.api.TransactionQueue;
import org.neo4j.kernel.impl.api.TransactionToApply;
import org.neo4j.kernel.impl.transaction.CommittedTransactionRepresentation;
import org.neo4j.kernel.impl.transaction.TransactionRepresentation;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.LogVersionRepository;
import org.neo4j.kernel.impl.transaction.log.LogicalTransactionStore;
import org.neo4j.kernel.impl.transaction.log.PhysicalLogFiles;
import org.neo4j.kernel.impl.transaction.log.TransactionCursor;
import org.neo4j.kernel.impl.transaction.log.TransactionIdStore;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryStart;
import org.neo4j.storageengine.api.StorageEngine;
import static org.neo4j.storageengine.api.TransactionApplicationMode.RECOVERY;

public class DefaultRecoverySPI implements Recovery.SPI, Visitor<CommittedTransactionRepresentation,Exception>
{
    private final PhysicalLogFiles logFiles;
    private final FileSystemAbstraction fileSystemAbstraction;
    private final LogVersionRepository logVersionRepository;
    private final PositionToRecoverFrom positionToRecoverFrom;
    private final StorageEngine storageEngine;
    private final TransactionIdStore transactionIdStore;
    private final LogicalTransactionStore logicalTransactionStore;
    private TransactionQueue transactionsToApply;

    public DefaultRecoverySPI(
            StorageEngine storageEngine,
            PhysicalLogFiles logFiles, FileSystemAbstraction fileSystemAbstraction,
            LogVersionRepository logVersionRepository, LatestCheckPointFinder checkPointFinder,
            TransactionIdStore transactionIdStore, LogicalTransactionStore logicalTransactionStore )
    {
        this.storageEngine = storageEngine;
        this.logFiles = logFiles;
        this.fileSystemAbstraction = fileSystemAbstraction;
        this.logVersionRepository = logVersionRepository;
        this.transactionIdStore = transactionIdStore;
        this.logicalTransactionStore = logicalTransactionStore;
        this.positionToRecoverFrom = new PositionToRecoverFrom( checkPointFinder );
    }

    @Override
    public void forceEverything()
    {
        IOLimiter unlimited = IOLimiter.unlimited(); // Runs during recovery; go as fast as possible.
        storageEngine.flushAndForce( unlimited );
    }

    @Override
    public LogPosition getPositionToRecoverFrom() throws IOException
    {
        return positionToRecoverFrom.apply( logVersionRepository.getCurrentLogVersion() );
    }

    @Override
    public Visitor<CommittedTransactionRepresentation,Exception> getRecoveryVisitor()
    {
        // Calling this method means that recovery is required, tell storage engine about it
        // This method will be called before recovery actually starts and so will ensure that
        // each store is aware that recovery will be performed. At this point all the stores have
        // already started btw.
        // Go and read more at {@link CommonAbstractStore#deleteIdGenerator()}
        storageEngine.prepareForRecoveryRequired();

        transactionsToApply = new TransactionQueue( 10_000, (first,last) -> storageEngine.apply( first, RECOVERY ) );

        return this;
    }

    @Override
    public TransactionCursor getTransactions( LogPosition position ) throws IOException
    {
        return logicalTransactionStore.getTransactions( position );
    }

    @Override
    public void allTransactionsRecovered( CommittedTransactionRepresentation lastRecoveredTransaction,
            LogPosition positionAfterLastRecoveredTransaction ) throws Exception
    {
        transactionsToApply.empty();
        transactionIdStore.setLastCommittedAndClosedTransactionId(
                lastRecoveredTransaction.getCommitEntry().getTxId(),
                LogEntryStart.checksum( lastRecoveredTransaction.getStartEntry() ),
                lastRecoveredTransaction.getCommitEntry().getTimeWritten(),
                positionAfterLastRecoveredTransaction.getByteOffset(),
                positionAfterLastRecoveredTransaction.getLogVersion() );
        // TODO: Also truncate last log after last known position
    }

    @Override
    public boolean visit( CommittedTransactionRepresentation transaction ) throws Exception
    {
        TransactionRepresentation txRepresentation = transaction.getTransactionRepresentation();
        long txId = transaction.getCommitEntry().getTxId();
        transactionsToApply.queue( new TransactionToApply( txRepresentation, txId ) );
        return false;
    }
}
