import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { api } from '../../api/client';
import type { ClassGroup } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { Tabs } from '../../components/ui/Tabs';
import { COMPONENT_LABELS } from '../../i18n/labels';
import { FinalGrades } from './FinalGrades';
import { Gradebook } from './Gradebook';

export function LecturerGroupPage() {
    const groupId = encodeURIComponent(useParams().groupId ?? '');
    const groups = useQuery({
        queryKey: ['lecturer', 'groups'],
        queryFn: () => api<ClassGroup[]>('/api/lecturer/groups'),
    });
    const group = groups.data?.find((g) => encodeURIComponent(g.id) === groupId);
    const title = group
        ? `${group.courseName}, ${COMPONENT_LABELS[group.componentType].toLowerCase()}, grupa ${group.groupNumber}`
        : 'Grupa';

    return (
        <>
            <PageHeader title={title} crumbs={[{ label: 'Moje grupy', to: '/teaching' }, { label: title }]} />
            {group && (
                <Tabs
                    label="Prowadzenie grupy"
                    tabs={[
                        { id: 'partial', label: 'Oceny cząstkowe', content: <Gradebook group={group} /> },
                        { id: 'final', label: 'Oceny końcowe', content: <FinalGrades group={group} /> },
                    ]}
                />
            )}
        </>
    );
}
