import { useEffect, useMemo } from 'react';
import ReactFlow, {
  type Node,
  type Edge,
  Controls,
  Background,
  MiniMap,
  MarkerType,
  useNodesState,
  useEdgesState,
} from 'reactflow';
import 'reactflow/dist/style.css';
import dagre from '@dagrejs/dagre';
import type { TraversalResultDTO } from '../types';

const NODE_W = 210;
const NODE_H = 44;

const DEPTH_COLORS: Record<number, string> = {
  0: '#1e3a5f',
  1: '#fef3c7',
  2: '#d1fae5',
  3: '#ede9fe',
};

function depthColor(depth: number | undefined): string {
  if (depth === undefined) return DEPTH_COLORS[0];
  return DEPTH_COLORS[Math.min(depth, 3)] ?? '#f1f5f9';
}

function layoutNodes(nodes: Node[], edges: Edge[]): Node[] {
  const g = new dagre.graphlib.Graph();
  g.setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: 'TB', ranksep: 70, nodesep: 40 });
  nodes.forEach(n => g.setNode(n.id, { width: NODE_W, height: NODE_H }));
  edges.forEach(e => g.setEdge(e.source, e.target));
  dagre.layout(g);
  return nodes.map(n => {
    const pos = g.node(n.id);
    return { ...n, position: { x: pos.x - NODE_W / 2, y: pos.y - NODE_H / 2 } };
  });
}

interface Props {
  result: TraversalResultDTO | null;
}

export function DependencyGraph({ result }: Props) {
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const cycleNodeNames = useMemo(
    () => new Set(result?.cycles.flat() ?? []),
    [result],
  );

  useEffect(() => {
    if (!result) {
      setNodes([]);
      setEdges([]);
      return;
    }

    const nodeStyle = (name: string, depth?: number) => ({
      borderRadius: 6,
      fontSize: 13,
      padding: '6px 10px',
      border: cycleNodeNames.has(name) ? '2px solid #ef4444' : '1px solid #d1d5db',
      background: depth === undefined ? depthColor(0) : depthColor(depth),
      color: depth === undefined ? 'white' : '#111827',
      fontWeight: depth === undefined ? 600 : 400,
    });

    const rawNodes: Node[] = [
      {
        id: result.origin,
        data: { label: result.origin },
        position: { x: 0, y: 0 },
        style: nodeStyle(result.origin),
      },
      ...result.services.map(s => ({
        id: s.name,
        data: { label: `${s.name}  [d${s.depth}]` },
        position: { x: 0, y: 0 },
        style: nodeStyle(s.name, s.depth),
      })),
    ];

    const rawEdges: Edge[] = result.edges.map(e => ({
      id: `${e.from}→${e.to}`,
      source: e.from,
      target: e.to,
      label: e.dependencyType !== 'RUNTIME' ? e.dependencyType : undefined,
      style: { stroke: '#6b7280' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#6b7280' },
    }));

    setNodes(layoutNodes(rawNodes, rawEdges));
    setEdges(rawEdges);
  }, [result, cycleNodeNames, setNodes, setEdges]);

  if (!result) {
    return (
      <div className="graph-placeholder">
        No traversal result
      </div>
    );
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      fitView
      fitViewOptions={{ padding: 0.2 }}
      minZoom={0.1}
      attributionPosition="bottom-right"
    >
      <Controls />
      <MiniMap nodeColor={n => (n.style?.background as string) ?? '#f9fafb'} />
      <Background gap={16} />
    </ReactFlow>
  );
}
