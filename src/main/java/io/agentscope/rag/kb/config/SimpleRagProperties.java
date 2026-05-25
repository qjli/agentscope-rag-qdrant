package io.agentscope.rag.kb.config;

import io.agentscope.core.rag.reader.SplitStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agentscope.rag.simple")
public class SimpleRagProperties {

    private boolean enabled = true;

    private StoreType storeType = StoreType.QDRANT;

    @Valid
    @NotNull
    private EmbeddingProperties embedding = new EmbeddingProperties();

    @Valid
    @NotNull
    private QdrantProperties qdrant = new QdrantProperties();

    @Valid
    @NotNull
    private ReaderProperties reader = new ReaderProperties();

    @Valid
    @NotNull
    private RetrieveProperties retrieve = new RetrieveProperties();

    @Valid
    @NotNull
    private FaqProperties faq = new FaqProperties();

    public enum StoreType {
        MEMORY,
        QDRANT
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public StoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(StoreType storeType) {
        this.storeType = storeType;
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding;
    }

    public QdrantProperties getQdrant() {
        return qdrant;
    }

    public void setQdrant(QdrantProperties qdrant) {
        this.qdrant = qdrant;
    }

    public ReaderProperties getReader() {
        return reader;
    }

    public void setReader(ReaderProperties reader) {
        this.reader = reader;
    }

    public RetrieveProperties getRetrieve() {
        return retrieve;
    }

    public void setRetrieve(RetrieveProperties retrieve) {
        this.retrieve = retrieve;
    }

    public FaqProperties getFaq() {
        return faq;
    }

    public void setFaq(FaqProperties faq) {
        this.faq = faq;
    }

    public static class EmbeddingProperties {

        @NotBlank
        private String apiKey;

        private String modelName = "text-embedding-v3";

        @Min(128)
        @Max(4096)
        private int dimensions = 1024;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class QdrantProperties {

        @NotBlank
        private String location = "http://localhost:6333";

        @NotBlank
        private String collectionName = "agentscope_kb";

        private String apiKey;

        private boolean useTls;

        private boolean checkCompatibility = false;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean isUseTls() {
            return useTls;
        }

        public void setUseTls(boolean useTls) {
            this.useTls = useTls;
        }

        public boolean isCheckCompatibility() {
            return checkCompatibility;
        }

        public void setCheckCompatibility(boolean checkCompatibility) {
            this.checkCompatibility = checkCompatibility;
        }
    }

    public static class ReaderProperties {

        @Min(128)
        @Max(8192)
        private int chunkSize = 1024;

        private SplitStrategy splitStrategy = SplitStrategy.PARAGRAPH;

        @Min(0)
        @Max(512)
        private int chunkOverlap = 50;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public SplitStrategy getSplitStrategy() {
            return splitStrategy;
        }

        public void setSplitStrategy(SplitStrategy splitStrategy) {
            this.splitStrategy = splitStrategy;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }

    public static class RetrieveProperties {

        @Min(1)
        @Max(50)
        private int limit = 5;

        @Min(0)
        @Max(1)
        private double scoreThreshold = 0.35;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }
    }

    public static class FaqProperties {

        private boolean bootstrapOnStartup;

        @NotBlank
        private String dataLocation = "classpath:faq/faq-items.json";

        private boolean reloadEnabled = true;

        public boolean isBootstrapOnStartup() {
            return bootstrapOnStartup;
        }

        public void setBootstrapOnStartup(boolean bootstrapOnStartup) {
            this.bootstrapOnStartup = bootstrapOnStartup;
        }

        public String getDataLocation() {
            return dataLocation;
        }

        public void setDataLocation(String dataLocation) {
            this.dataLocation = dataLocation;
        }

        public boolean isReloadEnabled() {
            return reloadEnabled;
        }

        public void setReloadEnabled(boolean reloadEnabled) {
            this.reloadEnabled = reloadEnabled;
        }
    }
}
