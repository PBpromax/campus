function pad(value: number): string { return value.toString().padStart(2,'0'); }
export function pickTime(current: string, accept: (value: string) => void): void {
  const parsed = new Date(current);
  const selected = Number.isNaN(parsed.getTime()) ? new Date() : parsed;
  let picked: Date | undefined = undefined;
  DatePickerDialog.show({
    start: new Date(), selected: selected,
    onDateAccept: (date: Date): void => { picked = date; },
    onDidDisappear: (): void => {
      const date = picked;
      if (date === undefined) { return; }
      TimePickerDialog.show({ selected: selected, useMilitaryTime: true,
        onAccept: (time: TimePickerResult): void => {
          accept(date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + 'T' + pad(time.hour) + ':' + pad(time.minute));
        }
      });
    }
  });
}
