export type Role = 'STUDENT' | 'LECTURER' | 'DEAN_OFFICE' | 'ADMIN';

export interface Me {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    role: Role;
    albumNumber: string | null;
    facultyId: string | null;
    academicTitle: string | null;
}