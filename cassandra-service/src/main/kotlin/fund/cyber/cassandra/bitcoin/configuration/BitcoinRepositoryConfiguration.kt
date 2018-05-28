package fund.cyber.cassandra.bitcoin.configuration

import com.datastax.driver.core.Cluster
import com.datastax.driver.extras.codecs.jdk8.InstantCodec
import fund.cyber.cassandra.bitcoin.repository.BitcoinContractSummaryRepository
import fund.cyber.cassandra.bitcoin.repository.BitcoinBlockRepository
import fund.cyber.cassandra.bitcoin.repository.BitcoinContractTxRepository
import fund.cyber.cassandra.bitcoin.repository.BitcoinTxRepository
import fund.cyber.cassandra.bitcoin.repository.PageableBitcoinContractMinedBlockRepository
import fund.cyber.cassandra.bitcoin.repository.PageableBitcoinContractTxRepository
import fund.cyber.cassandra.bitcoin.repository.PageableBitcoinBlockTxRepository
import fund.cyber.cassandra.common.NoChainCondition
import fund.cyber.cassandra.common.defaultKeyspaceSpecification
import fund.cyber.cassandra.configuration.CassandraRepositoriesConfiguration
import fund.cyber.cassandra.configuration.REPOSITORY_NAME_DELIMETER
import fund.cyber.cassandra.configuration.getKeyspaceSession
import fund.cyber.cassandra.configuration.keyspace
import fund.cyber.cassandra.configuration.mappingContext
import fund.cyber.cassandra.migration.BlockchainMigrationSettings
import fund.cyber.cassandra.migration.MigrationSettings
import fund.cyber.search.configuration.CASSANDRA_HOSTS
import fund.cyber.search.configuration.CASSANDRA_HOSTS_DEFAULT
import fund.cyber.search.configuration.CASSANDRA_PORT
import fund.cyber.search.configuration.CASSANDRA_PORT_DEFAULT
import fund.cyber.search.configuration.CHAIN
import fund.cyber.search.configuration.env
import fund.cyber.search.model.chains.BitcoinFamilyChain
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.data.cassandra.ReactiveSession
import org.springframework.data.cassandra.config.CassandraSessionFactoryBean
import org.springframework.data.cassandra.config.ClusterBuilderConfigurer
import org.springframework.data.cassandra.core.CassandraTemplate
import org.springframework.data.cassandra.core.ReactiveCassandraOperations
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification
import org.springframework.data.cassandra.core.cql.session.DefaultBridgedReactiveSession
import org.springframework.data.cassandra.core.cql.session.DefaultReactiveSessionFactory
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.data.cassandra.repository.support.CassandraRepositoryFactory
import org.springframework.data.cassandra.repository.support.ReactiveCassandraRepositoryFactory
import org.springframework.stereotype.Component


@Configuration
@EnableReactiveCassandraRepositories(
        basePackages = ["fund.cyber.cassandra.bitcoin.repository"],
        reactiveCassandraTemplateRef = "bitcoinCassandraTemplate"
)
@Conditional(BitcoinFamilyChainCondition::class)
class BitcoinRepositoryConfiguration(
        @Value("\${$CASSANDRA_HOSTS:$CASSANDRA_HOSTS_DEFAULT}")
        private val cassandraHosts: String,
        @Value("\${$CASSANDRA_PORT:$CASSANDRA_PORT_DEFAULT}")
        private val cassandraPort: Int
) : CassandraRepositoriesConfiguration(cassandraHosts, cassandraPort) {

    private val chain = BitcoinFamilyChain.valueOf(env(CHAIN, ""))

    override fun getKeyspaceName(): String = chain.keyspace
    override fun getEntityBasePackages(): Array<String> = arrayOf("fund.cyber.cassandra.bitcoin.model")

    override fun getKeyspaceCreations(): List<CreateKeyspaceSpecification> {
        return super.getKeyspaceCreations() + listOf(defaultKeyspaceSpecification(chain.lowerCaseName))
    }

    @Bean
    fun migrationSettings(): MigrationSettings {
        return BlockchainMigrationSettings(chain)
    }

    @Bean("bitcoinCassandraTemplate")
    fun reactiveCassandraTemplate(
            @Qualifier("bitcoinReactiveSession") session: ReactiveSession
    ): ReactiveCassandraOperations {
        return ReactiveCassandraTemplate(DefaultReactiveSessionFactory(session), cassandraConverter())
    }

    @Bean("bitcoinReactiveSession")
    fun reactiveSession(
            @Qualifier("bitcoinSession") session: CassandraSessionFactoryBean
    ): ReactiveSession {
        return DefaultBridgedReactiveSession(session.`object`)
    }

    override fun getClusterBuilderConfigurer(): ClusterBuilderConfigurer? {
        return ClusterBuilderConfigurer { clusterBuilder ->
            clusterBuilder.configuration.codecRegistry.register(InstantCodec.instance)
            return@ClusterBuilderConfigurer clusterBuilder
        }
    }

    @Bean("bitcoinSession")
    override fun session(): CassandraSessionFactoryBean {
        val session = super.session()
        session.setKeyspaceName(keyspaceName)
        return session
    }
}


private class BitcoinFamilyChainCondition : Condition {

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {

        val chain = context.environment.getProperty(CHAIN) ?: ""
        return BitcoinFamilyChain.values().map(BitcoinFamilyChain::name).contains(chain)
    }
}


@Component("bitcoin-cassandra-repositories")
@Conditional(NoChainCondition::class)
class BitcoinRepositoriesConfiguration : InitializingBean {

    @Autowired
    private lateinit var applicationContext: GenericApplicationContext
    @Autowired
    private lateinit var cluster: Cluster

    override fun afterPropertiesSet() {
        registerBitcoinRepositories()
    }

    fun registerBitcoinRepositories() {

        val beanFactory = applicationContext.beanFactory

        cluster.metadata.keyspaces
                .filter { keyspace -> keyspace.name.startsWith("bitcoin", true) }
                .forEach { keyspace ->

                    //create sessions
                    val converter = MappingCassandraConverter(mappingContext(cluster, keyspace.name,
                        "fund.cyber.cassandra.bitcoin.model"))
                    val session = getKeyspaceSession(cluster, keyspace.name, converter).also { it.afterPropertiesSet() }
                    val reactiveSession = DefaultReactiveSessionFactory(DefaultBridgedReactiveSession(session.`object`))

                    // create cassandra operations
                    val reactiveCassandraOperations = ReactiveCassandraTemplate(reactiveSession, converter)
                    val cassandraOperations = CassandraTemplate(session.`object`, converter)

                    // create repository factories
                    val reactiveRepositoryFactory = ReactiveCassandraRepositoryFactory(reactiveCassandraOperations)
                    val repositoryFactory = CassandraRepositoryFactory(cassandraOperations)

                    // create repositories
                    val blockRepository = reactiveRepositoryFactory.getRepository(BitcoinBlockRepository::class.java)
                    val blockTxRepository = repositoryFactory
                            .getRepository(PageableBitcoinBlockTxRepository::class.java)

                    val txRepository = reactiveRepositoryFactory.getRepository(BitcoinTxRepository::class.java)

                    val contractRepository = reactiveRepositoryFactory
                            .getRepository(BitcoinContractSummaryRepository::class.java)
                    val contractTxRepository = reactiveRepositoryFactory
                        .getRepository(BitcoinContractTxRepository::class.java)
                    val pageableContractTxRepository = repositoryFactory
                            .getRepository(PageableBitcoinContractTxRepository::class.java)
                    val contractBlockRepository = repositoryFactory
                            .getRepository(PageableBitcoinContractMinedBlockRepository::class.java)

                    val repositoryPrefix = "${keyspace.name}$REPOSITORY_NAME_DELIMETER"

                    // register repositories
                    beanFactory.registerSingleton("${repositoryPrefix}blockRepository", blockRepository)
                    beanFactory.registerSingleton("${repositoryPrefix}pageableBlockTxRepository", blockTxRepository)

                    beanFactory.registerSingleton("${repositoryPrefix}txRepository", txRepository)

                    beanFactory.registerSingleton("${repositoryPrefix}contractRepository", contractRepository)
                    beanFactory.registerSingleton("${repositoryPrefix}contractTxRepository",
                        contractTxRepository)
                    beanFactory.registerSingleton("${repositoryPrefix}pageableContractTxRepository",
                                    pageableContractTxRepository)
                    beanFactory.registerSingleton("${repositoryPrefix}pageableContractBlockRepository",
                                    contractBlockRepository)
                }
    }

}
