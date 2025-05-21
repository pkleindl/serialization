import com.alibaba.fastjson2.JSON;
import com.esotericsoftware.kryo.Kryo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.examples.streams.avro.microservices.*;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

class ObjectCopyComparisonTest {
    private static final String SCHEMA_REGISTRY_SCOPE = ObjectCopyComparisonTest.class.getName();
    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    abstract class IgnoreSchemaProperty
    {
        @JsonIgnore
        abstract void getSchema();

        @JsonIgnore
        abstract org.apache.avro.specific.SpecificData getSpecificData();
    }

    @Test
    void test() throws Exception {
        System.setProperty("org.apache.avro.fastread", "true");
        System.setProperty("org.apache.avro.specific.use_custom_coders", "true");

        final Schema schema = new Schema.Parser().parse(
                ObjectCopyComparisonTest.class.getResourceAsStream("/eventaggregate.avsc")
        );

        final SchemaRegistryClient schemaRegistryClient = MockSchemaRegistry.getClientForScope(SCHEMA_REGISTRY_SCOPE);

        schemaRegistryClient.register("inputTopic-value", new AvroSchema(schema));

        final HashMap<String, String> map = new HashMap<>();
        map.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);


        final SpecificAvroSerializer<EventAggregate> eventAggregateSpecificAvroSerializer = new SpecificAvroSerializer<>();
        eventAggregateSpecificAvroSerializer.configure(map, false);

        final SpecificAvroDeserializer<EventAggregate> eventAggregateSpecificAvroDeserializer = new SpecificAvroDeserializer<>();
        eventAggregateSpecificAvroDeserializer.configure(map, false);

        EventAggregate eve = new EventAggregate();
        eve.setId(1);
        eve.setEventList(new ArrayList<>());
        eve.setSecondAggregateList(new ArrayList<>());
        eve.setThirdAggregateList(new ArrayList<>());

        FirstAggregate firstAggregate = new FirstAggregate();
        firstAggregate.setId(1);
        firstAggregate.setAction("update");
        List<SomeInformation> someInformations = new ArrayList<>();
        for (long k = 1; k <= 100; k++) {
            SomeInformation someInformation = new SomeInformation();
            someInformation.setInformation("RandomString");
            //someInformation.setInformation("RandomString" + k);
            someInformations.add(someInformation);
        }
        firstAggregate.setSomeList(someInformations);
        eve.setFirstAggregateList(Collections.singletonList(firstAggregate));

        long start;

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = SpecificData.get().deepCopy(EventAggregate.getClassSchema(), eve);
        }
        System.out.println("SpecificData.get().deepCopy:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = EventAggregate.newBuilder(eve).build();
        }
        System.out.println("EventAggregate.newBuilder:" + (System.currentTimeMillis() - start));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.addMixIn(EventAggregate.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(FirstAggregate.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(SomeInformation.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(SecondAggregate.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(ThirdAggregate.class, IgnoreSchemaProperty.class);

        JSON.mixIn(EventAggregate.class, IgnoreSchemaProperty.class);
        JSON.mixIn(FirstAggregate.class, IgnoreSchemaProperty.class);
        JSON.mixIn(SomeInformation.class, IgnoreSchemaProperty.class);
        JSON.mixIn(SecondAggregate.class, IgnoreSchemaProperty.class);
        JSON.mixIn(ThirdAggregate.class, IgnoreSchemaProperty.class);
        String json = JSON.toJSONString(eve);
        byte[] jsonBytes2 = JSON.toJSONBytes(eve);

        /*
        start = System.currentTimeMillis();
        TokenBuffer tb = new TokenBuffer(new ObjectMapper(), false);
        for (long i = 1; i <= 1000000; i++) {
            objectMapper.writeValue(tb, eve);
            EventAggregate eventAggregate = objectMapper.readValue(tb.asParser(), EventAggregate.class);
        }
        System.out.println("objectMapper.readValue(tb.asParser():" + (System.currentTimeMillis() - start));

         */

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = objectMapper.convertValue(eve, EventAggregate.class);
        }
        System.out.println("objectMapper.convertValue:" + (System.currentTimeMillis() - start));

        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = kryo.copy(eve);
        }
        System.out.println("kryo.copy:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = JSON.copy(eve);
        }
        System.out.println("JSON.copy:" + (System.currentTimeMillis() - start));

        /*
        System.out.println(eve);
        System.out.println(SpecificData.get().deepCopy(EventAggregate.getClassSchema(), eve).toString());
        System.out.println(kryo.copy(eve).toString());
        System.out.println(objectMapper.convertValue(eve, EventAggregate.class));

        System.out.println(eve.equals(SpecificData.get().deepCopy(EventAggregate.getClassSchema(), eve)) );
        System.out.println(eve.equals(kryo.copy(eve)) );
        System.out.println(SpecificData.get().deepCopy(EventAggregate.getClassSchema(), eve).equals(kryo.copy(eve)) );
        System.out.println(eve.equals(objectMapper.convertValue(eve, EventAggregate.class)) );

         */

        /* For reference, it's slow
        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = SerializationUtils.clone(eve);
        }
        System.out.println("SerializationUtils.clone:" + (System.currentTimeMillis() - start));
        */
        /*
        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            SpecificData specificData = SpecificData.get();
            specificData.setCustomCoders(true);
            specificData.setFastReaderEnabled(true);
            EventAggregate eventAggregate = specificData.deepCopy(EventAggregate.getClassSchema(), eve);
        }
        System.out.println("tbd:" + (System.currentTimeMillis() - start));

         */

    }

}
