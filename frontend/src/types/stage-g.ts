export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type CatalogItem = {
  id: string;
  code: string;
  name: string;
  active: boolean;
  displayOrder?: number;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type CatalogReference = Pick<CatalogItem, 'id' | 'code' | 'name'>;
export type UserReference = { id: string; name: string };
export type DeviceStatus = 'PENDENTE_MANUTENCAO' | 'DISPONIVEL_VENDA' | 'VENDIDO';
export type RegistrationOrigin = 'OPERATIONAL' | 'INITIAL_IMPORT';

export type DevicePhoto = {
  id: string;
  url: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  position: number;
  createdAt: string;
  createdBy: UserReference;
};

export type DeviceSummary = {
  id: string;
  internalCode: string;
  model: CatalogReference;
  color: CatalogReference;
  storageGb: number;
  purchasePrice: number;
  maintenanceTotal: number;
  investmentTotal: number;
  purchasedAt: string;
  status: DeviceStatus;
  registrationOrigin: RegistrationOrigin;
  archived: boolean;
  coverPhotoUrl: string | null;
  updatedAt: string;
  version: number;
};

export type DeviceDetail = DeviceSummary & {
  faceIdWorking: boolean;
  originalScreen: boolean;
  originalBattery: boolean;
  batteryHealthPercent: number | null;
  photos: DevicePhoto[];
  createdAt: string;
  createdBy: UserReference;
  updatedBy: UserReference;
  archivedAt: string | null;
  archivedBy: UserReference | null;
};

export type RegisterDeviceInput = {
  modelId: string;
  colorId: string;
  storageGb: number;
  purchasePrice: number;
  purchasedAt: string;
  faceIdWorking: boolean;
  originalScreen: boolean;
  originalBattery: boolean;
  batteryHealthPercent: number;
  initialStatus: Exclude<DeviceStatus, 'VENDIDO'>;
};

export type BusinessInitialization = {
  id?: string | null;
  status: 'NOT_STARTED' | 'PREPARING' | 'COMPLETED';
  cutoffAt: string | null;
  trackingStartedAt: string | null;
  version?: number | null;
};

export type BusinessInitializationPreview = {
  status: 'PREPARING' | 'COMPLETED';
  cutoffAt: string;
  initialDeviceCount: number;
  purchaseCapital: number;
  maintenanceCapital: number;
  stockCapital: number;
};
