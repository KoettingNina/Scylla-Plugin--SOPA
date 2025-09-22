package de.tum.insm.scylla.plugin.sopa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DistributionGeneratorTest {
    
    private CostDriverExecutionLoggingPlugin plugin;
    private static final int SAMPLE_SIZE = 10000;
    private static final double TOLERANCE = 0.1; // 10% tolerance for statistical tests
    
    @BeforeEach
    void setUp() {
        plugin = new CostDriverExecutionLoggingPlugin();
    }
    
  
        @Test
        void testExponentialDistribution() {
            double mean = 2.0;
        
        
        
        // Calculate sample mean
        double sampleMean = generateSamplesAndCalculateMean(mean, SAMPLE_SIZE, plugin::generateExponentialValueDesmoj);
        
       

        // Test if sample mean is close to expected mean
        assertEquals(mean, sampleMean, mean * TOLERANCE, 
            "Sample mean should be close to expected mean for exponential distribution");

    }
    @Test
    void testExponentialDistribution_invalidMean() {
        double mean = 0.0;
        assertThrows(IllegalArgumentException.class, () -> plugin.generateExponentialValueDesmoj(mean));
    }

    

    
        @Test
        void testNormalDistribution() {
            double mean = 5.0;
        double stdDev = 2.0;
        
        double[] stats = generateSamplesAndCalculateMeanAndStdDev(mean, stdDev, SAMPLE_SIZE, plugin::generateNormalValueDesmoj);
        double sampleMean = stats[0];
        double sampleStdDev = stats[1];


        // Test if sample statistics are close to expected values
        assertEquals(mean, sampleMean, mean * TOLERANCE, 
            "Sample mean should be close to expected mean for normal distribution");
        assertEquals(stdDev, sampleStdDev, stdDev * TOLERANCE, 
            "Sample standard deviation should be close to expected value for normal distribution");
    }  
    @Test
    void testNormalDistribution_invalidStdDev() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateNormalValueDesmoj(5.0, -1.0));
    }
 
    
    @Test
    void testUniformDistribution() {
        double lower = 1.0;
        double upper = 5.0;
        List<Double> samples = new ArrayList<>();
        
        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateUniformValueDesmoj(lower, upper);
            assertTrue(value >= lower && value <= upper, "Value should be between lower and upper bounds");
            samples.add(value);
        }
        
        // Calculate sample mean
        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double expectedMean = (lower + upper) / 2.0;
        
        // Test if sample mean is close to expected mean
        assertEquals(expectedMean, sampleMean, expectedMean * TOLERANCE, 
            "Sample mean should be close to expected mean for uniform distribution");
    }

    @Test
    void testUniformDistribution_lowerEqualsUpper() {
        double lower = 3.0;
        double upper = 3.0;
        // Überprüfe ob der Wert immer gleich lower und upper ist
        double value = plugin.generateUniformValueDesmoj(lower, upper);
        assertEquals(lower, value, "Der Wert sollte gleich lower sein, wenn lower == upper");

    }

    @Test
    void testUniformDistribution_invalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateUniformValueDesmoj(5.0, 1.0));
    }
    
    @Test
    void testErlangDistribution() {
        int order = 3;
        double mean = 2.0;
        List<Double> samples = new ArrayList<>();
        
        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateErlangValueDesmoj(order, mean);
            assertTrue(value >= 0, "Erlang value should be non-negative");
            samples.add(value);
        }
        
        // Calculate sample mean
        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Test if sample mean is close to expected mean
        assertEquals(mean, sampleMean, mean * TOLERANCE, 
            "Sample mean should be close to expected mean for Erlang distribution");
    }

    @Test
    void testErlangDistribution_invalidOrder() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateErlangValueDesmoj(0, 2.0));
    }

    @Test
    void testErlangDistribution_invalidMean() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateErlangValueDesmoj(3, 0.0));
    }
    
    @Test
    void testTriangularDistribution() {
        double lower = 1.0;
        double peak = 3.0;
        double upper = 5.0;
        List<Double> samples = new ArrayList<>();
        
        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateTriangularValueDesmoj(lower, peak, upper);
            assertTrue(value >= lower && value <= upper, 
                "Triangular value should be within bounds");
            samples.add(value);
        }
        
        // Calculate sample mean
        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double expectedMean = (lower + peak + upper) / 3.0;
        
        // Test if sample mean is close to expected mean
        assertEquals(expectedMean, sampleMean, expectedMean * TOLERANCE, 
            "Sample mean should be close to expected mean for triangular distribution");
    }

    @Test
    void testTriangularDistribution_invalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateTriangularValueDesmoj(5.0, 3.0, 7.0)); // lower > peak
        assertThrows(IllegalArgumentException.class, () -> plugin.generateTriangularValueDesmoj(1.0, 5.0, 3.0)); // peak > upper
    }

    @Test
    void testTriangularDistribution_peakEqualsLower(){
        double lower = 3.0;
        double peak = 3.0;
        double upper = 5.0;
        List<Double> samples = new ArrayList<>();

        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateTriangularValueDesmoj(lower, peak, upper);
            assertTrue(value >= lower && value <= upper, "Triangular value should be within bounds");
            samples.add(value);
        }
         double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
         double expectedMean = (lower + peak + upper) / 3.0;
        assertEquals(expectedMean, sampleMean, expectedMean * TOLERANCE, "Sample mean should be close to expected mean");
    }
    @Test
    void testTriangularDistribution_peakEqualsUpper(){
        double lower = 1.0;
        double peak = 5.0;
        double upper = 5.0;
        List<Double> samples = new ArrayList<>();

        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateTriangularValueDesmoj(lower, peak, upper);
            assertTrue(value >= lower && value <= upper, "Triangular value should be within bounds");
            samples.add(value);
        }
        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
         double expectedMean = (lower + peak + upper) / 3.0;
        assertEquals(expectedMean, sampleMean, expectedMean * TOLERANCE, "Sample mean should be close to expected mean");
    }
    
    @Test
    void testBinomialDistribution() {
        int n = 10;
        double p = 0.3;
        List<Double> samples = new ArrayList<>();
        
        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateBinomialValueDesmoj(n, p);
            assertTrue(value >= 0 && value <= n, 
                "Binomial value should be between 0 and n");

            //test if the value is an integer
            assertEquals(value, Math.floor(value), "Binomial value should be an integer (floor)");
            samples.add(value);
        }
        
        // Calculate sample mean
        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double expectedMean = n * p;
        
        // Test if sample mean is close to expected mean
        assertEquals(expectedMean, sampleMean, expectedMean * TOLERANCE, 
            "Sample mean should be close to expected mean for binomial distribution");
    }

    @Test
    void testBinomialDistribution_pEqualsZero() {
        int n = 10;
        double p = 0.0;
        List<Double> samples = new ArrayList<>();

        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateBinomialValueDesmoj(n, p);
            assertEquals(0.0, value, "For p=0, all values must be 0");
            samples.add(value);
        }

        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        assertEquals(0.0, sampleMean, "Mean should be 0 for p=0");

       
    }

    @Test
    void testBinomialDistribution_pEqualsOne() {
        int n = 10;
        double p = 1.0;
        List<Double> samples = new ArrayList<>();

        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateBinomialValueDesmoj(n, p);
            assertEquals((double) n, value, "For p=1, all values must be n");
            samples.add(value);
        }

        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        assertEquals((double) n, sampleMean, "Mean should be n for p=1");

        
    }

    @Test
    void testBinomialDistribution_nEqualsZero() {
        int n = 0;
        double p = 0.5; // p ist hier irrelevant, Ergebnis sollte immer 0 sein
        List<Double> samples = new ArrayList<>();

        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateBinomialValueDesmoj(n, p);
            assertEquals(0.0, value, "For n=0, all values must be 0");
            samples.add(value);
        }

        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        assertEquals(0.0, sampleMean, "Mean should be 0 for n=0");

       
    }

    @Test
    void testBinomialDistribution_invalidAmount() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateBinomialValueDesmoj(-1, 0.5));
    }

    @Test
    void testBinomialDistribution_invalidProbabilityNegative() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateBinomialValueDesmoj(10, -0.1));
    }

    @Test
    void testBinomialDistribution_invalidProbabilityTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> plugin.generateBinomialValueDesmoj(10, 1.1));
    }
    
    @Test
    void testArbitraryFiniteDistribution() {
        List<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> value1 = new HashMap<>();
        value1.put("value", 1.0);
       value1.put("frequency", 1.0);  
        values.add(value1);
        
        Map<String, Object> value2 = new HashMap<>();
        value2.put("value", 2.0);
        value2.put("frequency", 1.0);  
        values.add(value2);
        
        List<Double> samples = new ArrayList<>();
        
        // Generate samples
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateArbitraryFiniteValueDesmoj(values);
            assertTrue(value == 1.0 || value == 2.0, 
                "Arbitrary finite value should be one of the specified values");
            samples.add(value);
        }
        
        // Calculate relative frequencies
        long count1 = samples.stream().filter(v -> v == 1.0).count();
        long count2 = samples.stream().filter(v -> v == 2.0).count();
        
        double freq1 = (double) count1 / SAMPLE_SIZE;
        double freq2 = (double) count2 / SAMPLE_SIZE;
        
        // Expected frequencies (2:1 ratio)
        double expectedFreq1 = 1.0/2.0;
        double expectedFreq2 = 1.0/2.0;
        
        // Test if relative frequencies are close to expected values
        assertEquals(expectedFreq1, freq1, TOLERANCE, 
            "Frequency of first value should be close to expected frequency");
        assertEquals(expectedFreq2, freq2, TOLERANCE, 
            "Frequency of second value should be close to expected frequency");

        //test if the sum of the frequencies is 1
        double sumOfFrequencies = freq1 + freq2;
        assertEquals(1.0, sumOfFrequencies, 0.001, "Die Summe der relativen Frequenzen sollte 1 ergeben.");
    
    }

    @Test
    void testArbitraryFiniteDistribution_singleValue() {
        
        List<Map<String, Object>> values = new ArrayList<>();
        Map<String, Object> value1 = new HashMap<>();
        value1.put("value", 5.0);
        value1.put("frequency", 1.0);  
        values.add(value1);

        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            double value = plugin.generateArbitraryFiniteValueDesmoj(values);
            assertEquals(5.0, value, 0.0001, "Value should be 5.0 since it's the only option");
            samples.add(value);
        }
        
    }

    @Test
    void testArbitraryFiniteDistribution_invalidValues() {
        List<Map<String, Object>> values = new ArrayList<>();

        // Test mit null values - Wert
        Map<String, Object> value1 = new HashMap<>();
        value1.put("frequency", 1.0);  
        values.add(value1);
        assertThrows(IllegalArgumentException.class, () -> plugin.generateArbitraryFiniteValueDesmoj(values), "Sollte eine Exception werfen, wenn der Wert fehlt");
    }
    
    @Test
    void testArbitraryFiniteDistribution_emptyList() {
        List<Map<String, Object>> values = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> plugin.generateArbitraryFiniteValueDesmoj(values), "Sollte eine Exception werfen, wenn die Liste leer ist");
    }

    private double generateSamplesAndCalculateMean(double mean, int numberOfSamples, DistributionGeneratorFunc generator) {
        java.util.List<Double> samples = new java.util.ArrayList<>();
        for (int i = 0; i < numberOfSamples; i++) {
            samples.add(generator.generate(mean));
        }
        return samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double[] generateSamplesAndCalculateMeanAndStdDev(double mean, double stdDev, int numberOfSamples, DistributionGeneratorFuncDouble generator) {
        java.util.List<Double> samples = new java.util.ArrayList<>();
        for (int i = 0; i < numberOfSamples; i++) {
            samples.add(generator.generate(mean, stdDev));
        }
        double sampleMean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sampleStdDev = calculateStandardDeviation(samples, sampleMean);
        return new double[] { sampleMean, sampleStdDev };
    }

    interface DistributionGeneratorFunc {
        double generate(double param);
    }

    interface DistributionGeneratorFuncDouble {
        double generate(double param1, double param2);
    }
    
    // Helper method to calculate standard deviation
    private double calculateStandardDeviation(List<Double> values, double mean) {
        return Math.sqrt(values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0));
    }
} 