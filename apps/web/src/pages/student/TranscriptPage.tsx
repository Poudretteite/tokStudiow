import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { StudyTranscript } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { formatAverage } from '../../i18n/format';
import { ATTEMPT_LABELS, COMPONENT_LABELS } from '../../i18n/labels';

export function TranscriptPage() {
    const query = useQuery({
        queryKey: ['me', 'transcript'],
        queryFn: () => api<StudyTranscript[]>('/api/me/transcript'),
    });

    return (
        <>
            <PageHeader title="Indeks" crumbs={[{ label: 'Indeks' }]} />
            <QueryState query={query}>
                {(studies) =>
                    studies.length === 0 ? (
                        <p className="text-muted">Nie masz jeszcze przypisanych studiów.</p>
                    ) : (
                        <div className="flex flex-col gap-6">
                            {studies.map((study) => (
                                <Panel
                                    key={study.studyId}
                                    title={study.fieldOfStudyName}
                                    aside={
                                        <span className="text-sm">
                      Średnia:{' '}
                                            <strong>{study.average === null ? 'brak' : formatAverage(study.average)}</strong>
                    </span>
                                    }
                                    flush
                                >
                                    {study.entries.length === 0 ? (
                                        <p className="p-4 text-muted">Brak zatwierdzonych ocen.</p>
                                    ) : (
                                        <Table minWidth={640}>
                                            <thead>
                                            <tr>
                                                <Th>Przedmiot</Th>
                                                <Th>Forma zajęć</Th>
                                                <Th>Semestr</Th>
                                                <Th>Termin</Th>
                                                <Th>Ocena</Th>
                                                <Th className="text-right">ECTS</Th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {study.entries.map((entry, index) => (
                                                <Tr key={index}>
                                                    <Td>
                                                        {entry.courseName}
                                                        <div className="text-xs text-muted">{entry.courseCode}</div>
                                                    </Td>
                                                    <Td>{COMPONENT_LABELS[entry.componentType]}</Td>
                                                    <Td>{entry.academicTermName}</Td>
                                                    <Td>{ATTEMPT_LABELS[entry.attempt]}</Td>
                                                    <Td className={`font-semibold ${entry.passing ? '' : 'text-red-700'}`}>
                                                        {entry.gradeLabel}
                                                    </Td>
                                                    <Td className="text-right">{entry.ects}</Td>
                                                </Tr>
                                            ))}
                                            </tbody>
                                        </Table>
                                    )}
                                </Panel>
                            ))}
                        </div>
                    )
                }
            </QueryState>
        </>
    );
}