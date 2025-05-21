import com.alibaba.fastjson2.JSON;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


class ObjectSerDeComparisonTest {
    private static final String SCHEMA_REGISTRY_SCOPE = ObjectSerDeComparisonTest.class.getName();
    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    abstract static class IgnoreSchemaProperty {
        @JsonIgnore
        abstract void getSchema();

        @JsonIgnore
        abstract org.apache.avro.specific.SpecificData getSpecificData();
    }

    @Test
    void testObjectCopy() throws Exception {

        System.setProperty("org.apache.avro.fastread", "true");
        System.setProperty("org.apache.avro.specific.use_custom_coders", "true");

        final Schema schema = new Schema.Parser().parse(
                ObjectSerDeComparisonTest.class.getResourceAsStream("/eventaggregate.avsc")
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
        SomeInformation someInformation = new SomeInformation();
        someInformation.setInformation("RandomString");
        for (long k = 1; k <= 100; k++) {
            someInformations.add(someInformation);
        }
        firstAggregate.setSomeList(someInformations);
        eve.setFirstAggregateList(Collections.singletonList(firstAggregate));

        byte[] result = eventAggregateSpecificAvroSerializer.serialize("inputTopic", eve);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.addMixIn(EventAggregate.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(FirstAggregate.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(SomeInformation.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(SecondAggregate.class, IgnoreSchemaProperty.class);
        objectMapper.addMixIn(ThirdAggregate.class, IgnoreSchemaProperty.class);
        String string = objectMapper.writeValueAsString(eve);
        byte[] jsonBytes = objectMapper.writeValueAsBytes(eve);

        JSON.mixIn(EventAggregate.class, IgnoreSchemaProperty.class);
        JSON.mixIn(FirstAggregate.class, IgnoreSchemaProperty.class);
        JSON.mixIn(SomeInformation.class, IgnoreSchemaProperty.class);
        JSON.mixIn(SecondAggregate.class, IgnoreSchemaProperty.class);
        JSON.mixIn(ThirdAggregate.class, IgnoreSchemaProperty.class);
        String json = JSON.toJSONString(eve);
        byte[] jsonBytes2 = JSON.toJSONBytes(eve);

        long start;

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            byte[] bytes = eventAggregateSpecificAvroSerializer.serialize("inputTopic", eve);
        }
        System.out.println("SpecificAvroSerializer.serialize:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = eventAggregateSpecificAvroDeserializer.deserialize("inputTopic", result);
        }
        System.out.println("SpecificAvroDeserializer.deserialize:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            byte[] bytes = objectMapper.writeValueAsBytes(eve);
        }
        System.out.println("objectMapper.writeValueAsBytes:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = objectMapper.readValue(jsonBytes, EventAggregate.class);
        }
        System.out.println("objectMapper.readValue:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            String s = objectMapper.writeValueAsString(eve);
        }
        System.out.println("objectMapper.writeValueAsString:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = objectMapper.readValue(string, EventAggregate.class);
        }
        System.out.println("objectMapper.readValue String:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            byte[] bytes = JSON.toJSONBytes(eve);
        }
        System.out.println("JSON.toJSONBytes:" + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (long i = 1; i <= 1000000; i++) {
            EventAggregate eventAggregate = JSON.parseObject(jsonBytes2, EventAggregate.class);
        }
        System.out.println("JSON.parseObject:" + (System.currentTimeMillis() - start));

    }

}
