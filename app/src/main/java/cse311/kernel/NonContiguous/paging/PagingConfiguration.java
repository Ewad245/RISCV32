package cse311.kernel.NonContiguous.paging;

/**
 * Configuration class for setting up paging policies.
 * Follows the AbstractPagingGuide.md design principles.
 */
public class PagingConfiguration {

    public enum Policy {
        EAGER, DEMAND, CLOCK
    }

    /**
     * Configure a PagedMemoryManager with the specified policies.
     * 
     * @param manager           The PagedMemoryManager to configure
     * @param pagerPolicy       The paging policy to use (EAGER or DEMAND)
     * @param replacementPolicy The page replacement policy to use (currently CLOCK)
     */
    public static void configure(PagedMemoryManager manager, Policy pagerPolicy, Policy replacementPolicy) {
        // Create replacement policy
        ReplacementPolicy replacer = createReplacementPolicy(replacementPolicy);

        // Create pager policy
        Pager pager = createPager(pagerPolicy, manager, replacer);

        // Configure the manager
        manager.setPager(pager);
    }

    private static ReplacementPolicy createReplacementPolicy(Policy policy) {
        switch (policy) {
            case CLOCK:
                return new ClockPolicy();
            default:
                throw new IllegalArgumentException("Unsupported replacement policy: " + policy);
        }
    }

    private static Pager createPager(Policy policy, PagedMemoryManager manager, ReplacementPolicy replacer) {
        switch (policy) {
            case EAGER:
                return new EagerPager(manager, replacer);
            case DEMAND:
                return new DemandPager(manager, replacer);
            default:
                throw new IllegalArgumentException("Unsupported pager policy: " + policy);
        }
    }
}