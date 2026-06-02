package mchorse.bbs_mod.particles;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParticleCurve
{
    public ParticleCurveType type = ParticleCurveType.LINEAR;
    public final List<MolangExpression> nodes = new SyncList();
    public List<CurveNode> parsedNodes = new ArrayList<>();
    public MolangExpression input = MolangParser.ZERO;
    public MolangExpression range = MolangParser.ZERO;
    public Variable variable;

    public static class CurveNode
    {
        public double time;
        public MolangExpression value = MolangParser.ZERO;
        public MolangExpression leftValue = MolangParser.ZERO;
        public MolangExpression rightValue = MolangParser.ZERO;
        public MolangExpression slope = MolangParser.ZERO;
        public MolangExpression leftSlope = MolangParser.ZERO;
        public MolangExpression rightSlope = MolangParser.ZERO;
    }

    public ParticleCurve()
    {
        this.nodes.add(MolangParser.ZERO);
        this.nodes.add(MolangParser.ONE);
        this.nodes.add(MolangParser.ZERO);
    }

    public double compute()
    {
        double r = this.range.get();
        if (r == 0)
        {
            return this.computeCurve(0);
        }
        double factor = this.input.get() / r;
        if (Double.isNaN(factor) || Double.isInfinite(factor))
        {
            factor = 0;
        }
        return this.computeCurve(factor);
    }

    private double computeCurve(double factor)
    {
        int length = this.parsedNodes.size();

        if (length == 0)
        {
            return 0;
        }
        else if (length == 1)
        {
            return this.parsedNodes.get(0).value.get();
        }

        if (factor < 0)
        {
            factor = -(1 + factor);
        }

        factor = MathUtils.clamp(factor, 0, 1);

        if (this.type == ParticleCurveType.BEZIER)
        {
            if (length < 4)
            {
                return this.evaluateLinear(factor);
            }
            double y0 = this.parsedNodes.get(0).value.get();
            double y1 = this.parsedNodes.get(1).value.get();
            double y2 = this.parsedNodes.get(2).value.get();
            double y3 = this.parsedNodes.get(3).value.get();

            double oneMinusT = 1.0 - factor;
            return oneMinusT * oneMinusT * oneMinusT * y0 +
                   3.0 * oneMinusT * oneMinusT * factor * y1 +
                   3.0 * oneMinusT * factor * factor * y2 +
                   factor * factor * factor * y3;
        }

        if (this.type == ParticleCurveType.HERMITE)
        {
            if (length <= 3)
            {
                return this.parsedNodes.get(MathUtils.clamp((int) (factor * (length - 1)), 0, length - 1)).value.get();
            }

            factor *= (length - 3);
            int index = (int) factor + 1;

            CurveNode beforeFirst = this.getCurveNode(index - 1);
            CurveNode first = this.getCurveNode(index);
            CurveNode next = this.getCurveNode(index + 1);
            CurveNode afterNext = this.getCurveNode(index + 2);

            return Lerps.cubicHermite(beforeFirst.value.get(), first.value.get(), next.value.get(), afterNext.value.get(), factor % 1);
        }

        // LINEAR and BEZIER_CHAIN
        CurveNode firstNode = this.parsedNodes.get(0);
        if (factor <= firstNode.time)
        {
            return firstNode.value.get();
        }
        CurveNode lastNode = this.parsedNodes.get(length - 1);
        if (factor >= lastNode.time)
        {
            return lastNode.value.get();
        }

        CurveNode nodeA = null;
        CurveNode nodeB = null;
        for (int i = 0; i < length - 1; i++)
        {
            CurveNode a = this.parsedNodes.get(i);
            CurveNode b = this.parsedNodes.get(i + 1);
            if (factor >= a.time && factor <= b.time)
            {
                nodeA = a;
                nodeB = b;
                break;
            }
        }

        if (nodeA == null || nodeB == null)
        {
            return 0.0;
        }

        double tA = nodeA.time;
        double tB = nodeB.time;
        double u = (tB - tA <= 0.00001) ? 0.0 : (factor - tA) / (tB - tA);

        if (this.type == ParticleCurveType.BEZIER_CHAIN)
        {
            double p0 = nodeA.rightValue.get();
            double p3 = nodeB.leftValue.get();
            double p1 = p0 + (nodeA.rightSlope.get() * (tB - tA)) / 3.0;
            double p2 = p3 - (nodeB.leftSlope.get() * (tB - tA)) / 3.0;

            double oneMinusU = 1.0 - u;
            return oneMinusU * oneMinusU * oneMinusU * p0 +
                   3.0 * oneMinusU * oneMinusU * u * p1 +
                   3.0 * oneMinusU * u * u * p2 +
                   u * u * u * p3;
        }

        double yA = nodeA.rightValue.get();
        double yB = nodeB.leftValue.get();
        return Lerps.lerp(yA, yB, u);
    }

    private double evaluateLinear(double factor)
    {
        int length = this.parsedNodes.size();
        factor *= length - 1;
        int index = (int) factor;

        CurveNode first = this.getCurveNode(index);
        CurveNode next = this.getCurveNode(index + 1);

        return Lerps.lerp(first.value.get(), next.value.get(), factor % 1);
    }

    private CurveNode getCurveNode(int index)
    {
        if (index < 0)
        {
            return this.parsedNodes.get(0);
        }
        else if (index >= this.parsedNodes.size())
        {
            return this.parsedNodes.get(this.parsedNodes.size() - 1);
        }

        return this.parsedNodes.get(index);
    }

    public MapType toData()
    {
        MapType curve = new MapType();
        curve.putString("type", this.type.id);
        curve.put("input", this.input.toData());
        curve.put("horizontal_range", this.range.toData());

        if (this.type == ParticleCurveType.BEZIER_CHAIN)
        {
            ListType nodesList = new ListType();
            for (CurveNode node : this.parsedNodes)
            {
                MapType nodeMap = new MapType();
                nodeMap.putDouble("time", node.time);
                nodeMap.put("value", node.value.toData());
                nodeMap.put("left_value", node.leftValue.toData());
                nodeMap.put("right_value", node.rightValue.toData());
                nodeMap.put("slope", node.slope.toData());
                nodeMap.put("left_slope", node.leftSlope.toData());
                nodeMap.put("right_slope", node.rightSlope.toData());
                nodesList.add(nodeMap);
            }
            curve.put("nodes", nodesList);
        }
        else
        {
            ListType nodesList = new ListType();
            for (CurveNode node : this.parsedNodes)
            {
                nodesList.add(node.value.toData());
            }
            curve.put("nodes", nodesList);
        }

        return curve;
    }

    public void fromData(MapType data, MolangParser parser) throws MolangException
    {
        if (data.has("type")) this.type = ParticleCurveType.fromString(data.getString("type"));
        if (data.has("input")) this.input = parser.parseDataSilently(data.get("input"));
        if (data.has("horizontal_range")) this.range = parser.parseDataSilently(data.get("horizontal_range"));

        if (data.has("nodes"))
        {
            BaseType nodesBase = data.get("nodes");
            this.parsedNodes.clear();

            if (nodesBase.isList())
            {
                ListType nodesList = nodesBase.asList();
                int size = nodesList.size();
                for (int i = 0; i < size; i++)
                {
                    BaseType element = nodesList.get(i);
                    CurveNode node = new CurveNode();
                    node.time = size > 1 ? (double) i / (size - 1) : 0.0;
                    this.parseNodeElement(node, element, parser);
                    this.parsedNodes.add(node);
                }
            }
            else if (nodesBase.isMap())
            {
                MapType nodesMap = nodesBase.asMap();
                for (Map.Entry<String, BaseType> entry : nodesMap)
                {
                    CurveNode node = new CurveNode();
                    try
                    {
                        node.time = Double.parseDouble(entry.getKey());
                    }
                    catch (NumberFormatException e)
                    {
                        node.time = 0.0;
                    }
                    this.parseNodeElement(node, entry.getValue(), parser);
                    this.parsedNodes.add(node);
                }
            }

            this.parsedNodes.sort((a, b) -> Double.compare(a.time, b.time));

            if (this.nodes instanceof SyncList)
            {
                ((SyncList) this.nodes).clearSilently();

                for (CurveNode node : this.parsedNodes)
                {
                    ((SyncList) this.nodes).addSilently(node.value);
                }
            }
        }
    }

    private void parseNodeElement(CurveNode node, BaseType element, MolangParser parser) throws MolangException
    {
        if (element.isMap())
        {
            MapType map = element.asMap();
            if (map.has("time")) node.time = map.getDouble("time");
            
            node.value = parser.parseDataSilently(map.get("value"), MolangParser.ZERO);
            node.leftValue = parser.parseDataSilently(map.get("left_value"), node.value);
            node.rightValue = parser.parseDataSilently(map.get("right_value"), node.value);
            
            node.slope = parser.parseDataSilently(map.get("slope"), MolangParser.ZERO);
            node.leftSlope = parser.parseDataSilently(map.get("left_slope"), node.slope);
            node.rightSlope = parser.parseDataSilently(map.get("right_slope"), node.slope);
        }
        else
        {
            node.value = parser.parseDataSilently(element, MolangParser.ZERO);
            node.leftValue = node.value;
            node.rightValue = node.value;
            node.slope = MolangParser.ZERO;
            node.leftSlope = MolangParser.ZERO;
            node.rightSlope = MolangParser.ZERO;
        }
    }

    private class SyncList extends ArrayList<MolangExpression>
    {
        private boolean active = true;

        public void addSilently(MolangExpression expression)
        {
            this.active = false;
            this.add(expression);
            this.active = true;
        }

        public void clearSilently()
        {
            this.active = false;
            this.clear();
            this.active = true;
        }

        private void sync()
        {
            if (!this.active)
            {
                return;
            }

            ParticleCurve.this.parsedNodes.clear();

            int size = this.size();

            for (int i = 0; i < size; i++)
            {
                CurveNode node = new CurveNode();

                node.time = size > 1 ? (double) i / (size - 1) : 0.0;
                node.value = this.get(i);
                node.leftValue = node.value;
                node.rightValue = node.value;
                node.slope = MolangParser.ZERO;
                node.leftSlope = MolangParser.ZERO;
                node.rightSlope = MolangParser.ZERO;

                ParticleCurve.this.parsedNodes.add(node);
            }
        }

        @Override
        public MolangExpression set(int index, MolangExpression element)
        {
            MolangExpression old = super.set(index, element);

            this.sync();

            return old;
        }

        @Override
        public boolean add(MolangExpression e)
        {
            boolean result = super.add(e);

            this.sync();

            return result;
        }

        @Override
        public void add(int index, MolangExpression element)
        {
            super.add(index, element);

            this.sync();
        }

        @Override
        public MolangExpression remove(int index)
        {
            MolangExpression old = super.remove(index);

            this.sync();

            return old;
        }

        @Override
        public boolean remove(Object o)
        {
            boolean result = super.remove(o);

            this.sync();

            return result;
        }

        @Override
        public void clear()
        {
            super.clear();

            this.sync();
        }
    }
}