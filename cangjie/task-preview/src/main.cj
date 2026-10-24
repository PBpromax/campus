package campus_task_preview

import std.collection.*
import std.convert.*
import std.env.*
import std.sync.*

// ASCII-only bounded protocol. Parse the entire batch before projecting any event.
func run(): Int64 {
    let input=getStdIn()
    let header=input.readln().getOrThrow()
    let parallel=header=="CAMPUS_TRANSITION_V1\tPARALLEL"
    if(!parallel && header!="CAMPUS_TRANSITION_V1\tSERIAL") {throw IllegalArgumentException("HEADER")}
    let tasks=ArrayList<TaskSnapshot>();let events=ArrayList<TaskEvent>()
    var ended=false;var eventStarted=false;var bytes=header.size
    while(let Some(line) <- input.readln()) {
        bytes+=line.size
        if(line.size>256 || bytes>131072) {throw IllegalArgumentException("LIMIT")}
        if(line=="END") {ended=true;break}
        let fields=line.split("\t",removeEmpty: false)
        if(fields.size==5 && fields[0]=="TASK" && !eventStarted) {
            tasks.add(TaskSnapshot(Int64.parse(fields[1]),fields[2],TaskStatus.parse(fields[3]),Int64.parse(fields[4])))
        } else if(fields.size==6 && fields[0]=="EVENT") {
            eventStarted=true
            events.add(TaskEvent(Int64.parse(fields[1]),fields[2],Int64.parse(fields[3]),EventKind.parse(fields[4]),Participant.parse(fields[5])))
        } else {throw IllegalArgumentException("ROW")}
        if(tasks.size>128 || events.size>512) {throw IllegalArgumentException("LIMIT")}
    }
    if(!ended || tasks.isEmpty() || events.isEmpty()) {throw IllegalArgumentException("EMPTY_OR_TRUNCATED")}
    if(input.readln().isSome()) {throw IllegalArgumentException("TRAILING_INPUT")}
    let ids=HashSet<Int64>();for(task in tasks) {ids.add(task.id)}
    for(event in events) {if(!ids.contains(event.taskId)) {throw IllegalArgumentException("TASK_MISSING")}}
    let projection=EventProjection(tasks)
    let results=Array<String>(events.size,repeat: "")
    let workers=if(parallel) {min(4,events.size)} else {1}
    let cursor=AtomicInt64(0)
    let futures=ArrayList<Future<Unit>>()
    for(_ in 0..workers) {
        futures.add(spawn {
            while(true) {
                let index=cursor.fetchAdd(1)
                if(index>=events.size) {break}
                let event=events[index];let result=projection.apply(event)
                // Each worker owns a distinct index; immutable strings are emitted after all joins.
                results[index]="RESULT\t${index}\t${event.taskId}\t${event.id}\t${result.outcome}\t${result.status.code()}\t${result.version}"
            }
        })
    }
    for(future in futures) {future.get()}
    println("CAMPUS_TRANSITION_V1")
    for(result in results) {println(result)}
    println("SUMMARY\t${workers}\t${events.size}")
    return 0
}
main(): Int64 {
    try {return run()}
    catch(_: Exception) {println("ERROR\tINVALID_INPUT_OR_EXECUTION");return 2}
}
