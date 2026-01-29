export enum JobApplicationStatus {
    APPLIED = 'APPLIED',
    INTERVIEW = 'INTERVIEW',
    REJECTED = 'REJECTED',
    PASSED = 'PASSED',
    OFFER_RECEIVED = 'OFFER_RECEIVED'
}

export interface JobApplicationResponse {
    id: number;
    companyName: string;
    position: string;
    status: JobApplicationStatus;
    appliedDate: string;
    memo?: string;
    userId: number;
}
