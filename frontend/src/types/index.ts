export type DependencyType = 'RUNTIME' | 'BUILD' | 'OPTIONAL';

export interface ServiceDTO {
  name: string;
  description?: string;
  createdAt: string;
}

export interface RegisterServiceRequest {
  name: string;
  description?: string;
}

export interface DependencyDTO {
  fromService: string;
  toService: string;
  dependencyType: DependencyType;
  createdAt: string;
}

export interface AddDependencyRequest {
  dependsOnName: string;
  dependencyType: DependencyType;
}

export interface ServiceWithDepthDTO {
  name: string;
  description?: string;
  depth: number;
  createdAt?: string;
}

export interface EdgeDTO {
  from: string;
  to: string;
  dependencyType: DependencyType;
}

export interface TraversalResultDTO {
  origin: string;
  services: ServiceWithDepthDTO[];
  edges: EdgeDTO[];
  cycles: string[][];
}
