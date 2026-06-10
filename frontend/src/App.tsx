import { useCallback, useEffect, useState } from 'react';
import './App.css';
import { DependencyGraph } from './components/DependencyGraph';
import { api } from './api/client';
import type { ServiceDTO, DependencyDTO, TraversalResultDTO, DependencyType } from './types';

export default function App() {
  const [services, setServices] = useState<ServiceDTO[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [direction, setDirection] = useState<'downstream' | 'upstream'>('downstream');
  const [traversal, setTraversal] = useState<TraversalResultDTO | null>(null);
  const [directDeps, setDirectDeps] = useState<DependencyDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showRegister, setShowRegister] = useState(false);
  const [regName, setRegName] = useState('');
  const [regDesc, setRegDesc] = useState('');

  const [showAddDep, setShowAddDep] = useState(false);
  const [depTarget, setDepTarget] = useState('');
  const [depType, setDepType] = useState<DependencyType>('RUNTIME');

  const loadServices = useCallback(async () => {
    try {
      setServices(await api.listServices());
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => { loadServices(); }, [loadServices]);

  const loadTraversal = useCallback(async (name: string, dir: 'downstream' | 'upstream') => {
    setLoading(true);
    setError(null);
    try {
      const [tResult, deps] = await Promise.all([
        dir === 'downstream' ? api.getDownstream(name) : api.getUpstream(name),
        api.getDependencies(name),
      ]);
      setTraversal(tResult);
      setDirectDeps(deps);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const selectService = useCallback((name: string, dir?: 'downstream' | 'upstream') => {
    const d = dir ?? direction;
    setSelected(name);
    setDirection(d);
    setShowAddDep(false);
    loadTraversal(name, d);
  }, [direction, loadTraversal]);

  const switchDirection = (dir: 'downstream' | 'upstream') => {
    if (selected) selectService(selected, dir);
  };

  const handleRegister = async () => {
    const name = regName.trim();
    if (!name) return;
    try {
      await api.registerService({ name, description: regDesc.trim() || undefined });
      setRegName('');
      setRegDesc('');
      setShowRegister(false);
      await loadServices();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const handleDelete = async (name: string) => {
    if (!window.confirm(`Delete "${name}" and all its dependency edges?`)) return;
    try {
      await api.deleteService(name);
      if (selected === name) {
        setSelected(null);
        setTraversal(null);
        setDirectDeps([]);
      }
      await loadServices();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const handleAddDep = async () => {
    if (!selected || !depTarget) return;
    try {
      await api.addDependency(selected, { dependsOnName: depTarget, dependencyType: depType });
      setDepTarget('');
      setShowAddDep(false);
      loadTraversal(selected, direction);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const handleRemoveDep = async (toName: string) => {
    if (!selected) return;
    try {
      await api.removeDependency(selected, toName);
      loadTraversal(selected, direction);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Service Dependency Tracker</h1>
        {error && (
          <div className="error-banner">
            <span>{error}</span>
            <button className="btn-close" onClick={() => setError(null)}>✕</button>
          </div>
        )}
      </header>

      <div className="app-content">
        <aside className="service-panel">
          <div className="panel-title">
            <span>Services ({services.length})</span>
            <button className="btn-primary" onClick={() => setShowRegister(v => !v)}>
              {showRegister ? 'Cancel' : '+ Register'}
            </button>
          </div>

          {showRegister && (
            <div className="inline-form">
              <input
                placeholder="name  e.g. payment-service"
                value={regName}
                onChange={e => setRegName(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleRegister()}
                autoFocus
              />
              <input
                placeholder="description (optional)"
                value={regDesc}
                onChange={e => setRegDesc(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleRegister()}
              />
              <div className="form-actions">
                <button className="btn-primary" onClick={handleRegister}>Save</button>
                <button onClick={() => { setShowRegister(false); setRegName(''); setRegDesc(''); }}>
                  Cancel
                </button>
              </div>
            </div>
          )}

          <ul className="service-list">
            {services.map(s => (
              <li
                key={s.name}
                className={`service-item ${selected === s.name ? 'active' : ''}`}
                onClick={() => selectService(s.name)}
              >
                <span className="service-name" title={s.description}>{s.name}</span>
                <button
                  className="btn-danger-sm"
                  onClick={e => { e.stopPropagation(); handleDelete(s.name); }}
                  title="Delete service"
                >
                  Del
                </button>
              </li>
            ))}
            {services.length === 0 && (
              <li className="empty-hint">No services yet — register one above</li>
            )}
          </ul>
        </aside>

        <main className="graph-panel">
          {selected ? (
            <>
              <div className="graph-header">
                <span className="graph-title">{selected}</span>
                <div className="direction-toggle">
                  <button
                    className={direction === 'downstream' ? 'active' : ''}
                    onClick={() => switchDirection('downstream')}
                  >
                    Downstream
                  </button>
                  <button
                    className={direction === 'upstream' ? 'active' : ''}
                    onClick={() => switchDirection('upstream')}
                  >
                    Upstream
                  </button>
                </div>
                {loading && <span className="loading-indicator">Loading…</span>}
              </div>

              <div className="graph-canvas">
                <DependencyGraph result={traversal} />
              </div>

              {traversal && traversal.cycles.length > 0 && (
                <div className="cycle-warning">
                  ⚠ Cycles: {traversal.cycles.map(c => c.join(' → ')).join('  |  ')}
                </div>
              )}

              <div className="deps-panel">
                <div className="panel-title">
                  <span>Direct dependencies of <strong>{selected}</strong></span>
                  <button className="btn-primary" onClick={() => setShowAddDep(v => !v)}>
                    {showAddDep ? 'Cancel' : '+ Add'}
                  </button>
                </div>

                {showAddDep && (
                  <div className="inline-form inline-form--row">
                    <select value={depTarget} onChange={e => setDepTarget(e.target.value)}>
                      <option value="">— select service —</option>
                      {services
                        .filter(s => s.name !== selected)
                        .map(s => (
                          <option key={s.name} value={s.name}>{s.name}</option>
                        ))}
                    </select>
                    <select
                      value={depType}
                      onChange={e => setDepType(e.target.value as DependencyType)}
                    >
                      <option value="RUNTIME">RUNTIME</option>
                      <option value="BUILD">BUILD</option>
                      <option value="OPTIONAL">OPTIONAL</option>
                    </select>
                    <button className="btn-primary" onClick={handleAddDep}>Add</button>
                  </div>
                )}

                <ul className="dep-list">
                  {directDeps.map(d => (
                    <li key={d.toService} className="dep-item">
                      <span className="dep-name">{d.toService}</span>
                      <span className="dep-type-badge">{d.dependencyType}</span>
                      <button
                        className="btn-danger-sm"
                        onClick={() => handleRemoveDep(d.toService)}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                  {directDeps.length === 0 && !loading && (
                    <li className="empty-hint">No direct dependencies</li>
                  )}
                </ul>
              </div>
            </>
          ) : (
            <div className="empty-graph">
              Select a service from the left panel to view its dependency graph
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
