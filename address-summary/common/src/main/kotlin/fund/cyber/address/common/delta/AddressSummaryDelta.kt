package fund.cyber.address.common.delta

import fund.cyber.cassandra.common.CqlAddressSummary
import fund.cyber.search.model.events.PumpEvent
import org.apache.kafka.clients.consumer.ConsumerRecord

interface AddressSummaryDelta <S: CqlAddressSummary> {
    val address: String
    val topic: String
    val partition: Int
    val offset: Long

    fun createSummary(): S
    fun updateSummary(summary: S): S
}

interface DeltaProcessor<R, S: CqlAddressSummary, out D: AddressSummaryDelta<S>> {
    fun recordToDeltas(record: ConsumerRecord<PumpEvent, R>): List<D>
    fun affectedAddresses(records: List<ConsumerRecord<PumpEvent, R>>): Set<String>
}

interface DeltaMerger<D: AddressSummaryDelta<*>> {
    fun mergeDeltas(deltas: Iterable<D>, currentAddresses: Map<String, CqlAddressSummary>): D?
}