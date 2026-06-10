import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'min' })
export class MinPipe implements PipeTransform {
  transform(values: number[]): number { return values.length ? Math.min(...values) : 0; }
}
@Pipe({ name: 'max' })
export class MaxPipe implements PipeTransform {
  transform(values: number[]): number { return values.length ? Math.max(...values) : 0; }
}
@Pipe({ name: 'median' })
export class MedianPipe implements PipeTransform {
  transform(values: number[]): number {
    if (!values.length) return 0;
    const sorted = [...values].sort((a,b)=>a-b);
    const mid = Math.floor(sorted.length/2);
    return sorted.length % 2 === 0 ? (sorted[mid-1]+sorted[mid])/2 : sorted[mid];
  }
}
@Pipe({ name: 'stdDev' })
export class StdDevPipe implements PipeTransform {
  transform(values: number[]): number {
    if (values.length < 2) return 0;
    const mean = values.reduce((a,b)=>a+b,0)/values.length;
    const sq = values.map(v=>Math.pow(v-mean,2)).reduce((a,b)=>a+b,0);
    return Math.sqrt(sq/(values.length-1));
  }
}