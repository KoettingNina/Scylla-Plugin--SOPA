package cost_driver;

import org.springframework.lang.NonNull;

import java.util.List;

import java.util.stream.Collectors;

public class AbstractCostDriver extends CostDriver {

    @NonNull
    protected List<ConcreteCostDriver> children;

    public AbstractCostDriver(@NonNull String id, @NonNull List<ConcreteCostDriver> children) {
        super(id);
        this.children = children;
    }

    public List<ConcreteCostDriver> getChildren() {
        return this.children;
    }

    public void addChild(CostDriver concreteCostDriver) {
        children.add((ConcreteCostDriver) concreteCostDriver);
    }


    @Override
    public boolean equals(Object obj) {
        // If the object is compared with itself then return true
        if (obj == this) return true;

        /* Check if obj is an instance of Complex or not
          "null instanceof [type]" also returns false */
        if (!(obj instanceof AbstractCostDriver abstractCostDriver)) return false;

        return id.compareTo(abstractCostDriver.id) == 0 && children.equals(abstractCostDriver.children);
    }

    @Override
    public String toString() {
        return "AbstractCostDriver{" +
                "id='" + id + '\'' +
                ", children=" + children.stream()
                .map(ConcreteCostDriver::toString)
                .collect(Collectors.joining(", ")) +
                '}';
    }
}
