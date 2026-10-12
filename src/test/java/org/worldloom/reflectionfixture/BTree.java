package org.worldloom.reflectionfixture;

import com.artemis.Component;
import org.worldloom.serialize.SerializeParam;

/** Legal game component sharing its simple name with a stale legacy class. */
public class BTree extends Component {
    @SerializeParam public String treeName;
}
