package fund.cyber.cassandra.ethereum.model

import fund.cyber.cassandra.common.CqlAddressSummary
import fund.cyber.search.model.ethereum.EthereumBlock
import fund.cyber.search.model.ethereum.EthereumTx
import fund.cyber.search.model.ethereum.EthereumUncle
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("address_summary")
data class CqlEthereumAddressSummary(
        @PrimaryKey override val id: String,
        val confirmed_balance: BigDecimal,
        val contract_address: Boolean,
        val confirmed_total_received: BigDecimal,
        val tx_number: Int,
        val uncle_number: Int,
        val mined_block_number: Int,
        override val version: Long,
        override val kafka_delta_offset: Long,
        override val kafka_delta_partition: Int,
        override val kafka_delta_topic: String,
        override val kafka_delta_offset_committed: Boolean = false
) : CqlEthereumItem, CqlAddressSummary


@Table("tx_preview_by_address")
data class CqlEthereumAddressTxPreview(
        @PrimaryKeyColumn(ordinal = 0, type = PrimaryKeyType.PARTITIONED) val address: String,
        @PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.CLUSTERED) val fee: String,
        @PrimaryKeyColumn(ordinal = 2, type = PrimaryKeyType.CLUSTERED) val block_time: Instant,
        val hash: String,
        @Column(forceQuote = true) val from: String,
        @Column(forceQuote = true) val to: String,
        val value: String
) : CqlEthereumItem {

    constructor(tx: EthereumTx, address: String) : this(
            hash = tx.hash, address = address, block_time = tx.blockTime,
            from = tx.from, to = (tx.to ?: tx.createdContract)!!, //both 'to' or 'createdContract' can't be null at same time
            value = tx.value.toString(), fee = tx.fee.toString()
    )
}

@Table("mined_block_by_address")
data class CqlEthereumAddressMinedBlock(
        @PrimaryKeyColumn(ordinal = 0, type = PrimaryKeyType.PARTITIONED) val miner: String,
        @PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.CLUSTERED) val block_number: Long,
        val block_time: Instant,
        val block_reward: BigDecimal,
        val uncles_reward: BigDecimal,
        val tx_fees: BigDecimal,
        val tx_number: Int
) : CqlEthereumItem {
    constructor(block: EthereumBlock) : this(
            miner = block.miner, block_number = block.number, block_time = block.timestamp,
            block_reward = block.blockReward, uncles_reward = block.unclesReward,
            tx_fees = block.txFees, tx_number = block.txNumber
    )
}

@Table("uncle_by_address")
data class CqlEthereumAddressUncle(
        @PrimaryKeyColumn(ordinal = 0, type = PrimaryKeyType.PARTITIONED) val miner: String,
        @PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.CLUSTERED) val block_number: Long,
        val hash: String,
        val position: Int,
        val number: Long,
        val timestamp: Instant,
        val block_time: Instant,
        val block_hash: String,
        val uncle_reward: String
) : CqlEthereumItem {
    constructor(uncle: EthereumUncle) : this(
            hash = uncle.hash, position = uncle.position,
            number = uncle.number, timestamp = uncle.timestamp,
            block_number = uncle.blockNumber, block_time = uncle.blockTime, block_hash = uncle.blockHash,
            miner = uncle.miner, uncle_reward = uncle.uncleReward.toString()
    )
}