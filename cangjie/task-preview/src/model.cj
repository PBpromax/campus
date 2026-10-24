package campus_task_preview

import std.collection.*
import std.sync.*

// This model projects state only. It does not authorize or execute campus operations.
enum TaskStatus { Pending | InProgress | PendingConfirmation | Completed | Cancelled
    func code(): String {
        match(this) {
            case Pending => "PENDING"
            case InProgress => "IN_PROGRESS"
            case PendingConfirmation => "PENDING_CONFIRMATION"
            case Completed => "COMPLETED"
            case Cancelled => "CANCELLED"
        }
    }
    static func parse(value: String): TaskStatus {
        match(value) {
            case "PENDING" => Pending
            case "IN_PROGRESS" => InProgress
            case "PENDING_CONFIRMATION" => PendingConfirmation
            case "COMPLETED" => Completed
            case "CANCELLED" => Cancelled
            case _ => throw IllegalArgumentException("INVALID_STATUS")
        }
    }
}
enum EventKind { Accept | Submit | Confirm | Cancel
    func code(): String {match(this) {case Accept => "ACCEPT";case Submit => "SUBMIT";case Confirm => "CONFIRM";case Cancel => "CANCEL"}}
    static func parse(value: String): EventKind {
        match(value) {case "ACCEPT" => Accept;case "SUBMIT" => Submit;case "CONFIRM" => Confirm;case "CANCEL" => Cancel
            case _ => throw IllegalArgumentException("INVALID_EVENT")}
    }
}
enum Participant { Publisher | Acceptor | Other
    func code(): String {match(this) {case Publisher => "PUBLISHER";case Acceptor => "ACCEPTOR";case Other => "OTHER"}}
    static func parse(value: String): Participant {
        match(value) {case "PUBLISHER" => Publisher;case "ACCEPTOR" => Acceptor;case "OTHER" => Other
            case _ => throw IllegalArgumentException("INVALID_ROLE")}
    }
}
struct TaskSnapshot {
    let id: Int64
    let category: String
    let status: TaskStatus
    let version: Int64
    init(id: Int64, category: String, status: TaskStatus, version: Int64) {
        if(id<=0 || id>9007199254740991 || version<0 || version>9007199254740991 ||
            !["RUNNING","STUDY","BORROW","OTHER"].contains(category)) {throw IllegalArgumentException("INVALID_TASK")}
        this.id=id;this.category=category;this.status=status;this.version=version
    }
}
struct TaskEvent {
    let taskId: Int64
    let id: String
    let expectedVersion: Int64
    let kind: EventKind
    let participant: Participant
    init(taskId: Int64, id: String, expectedVersion: Int64, kind: EventKind, participant: Participant) {
        if(taskId<=0 || taskId>9007199254740991 || expectedVersion<0 || expectedVersion>9007199254740991 || !validId(id)) {throw IllegalArgumentException("INVALID_EVENT")}
        this.taskId=taskId;this.id=id;this.expectedVersion=expectedVersion;this.kind=kind;this.participant=participant
    }
    func fingerprint(): String {"${taskId}:${expectedVersion}:${kind.code()}:${participant.code()}"}
}
func validId(value: String): Bool {
    if(value.size<1 || value.size>64) {return false}
    for(byte in value.toArray()) {
        if(!((byte>=65 && byte<=90) || (byte>=97 && byte<=122) || (byte>=48 && byte<=57) || byte==95 || byte==45)) {return false}
    }
    return true
}
struct Decision {
    let outcome: String
    let status: TaskStatus
    let version: Int64
    init(outcome: String, status: TaskStatus, version: Int64) {this.outcome=outcome;this.status=status;this.version=version}
}
func reduce(snapshot: TaskSnapshot, event: TaskEvent): Decision {
    if(snapshot.id!=event.taskId) {return Decision("TASK_MISMATCH",snapshot.status,snapshot.version)}
    if(snapshot.version!=event.expectedVersion) {return Decision("VERSION_CONFLICT",snapshot.status,snapshot.version)}
    if(snapshot.version==9007199254740991) {return Decision("VERSION_EXHAUSTED",snapshot.status,snapshot.version)}
    var next: Option<TaskStatus>=None
    match((snapshot.status,event.kind,event.participant)) {
        case (TaskStatus.Pending,EventKind.Accept,Participant.Other) => next=Some(TaskStatus.InProgress)
        case (TaskStatus.InProgress,EventKind.Submit,Participant.Acceptor) => next=Some(TaskStatus.PendingConfirmation)
        case (TaskStatus.PendingConfirmation,EventKind.Confirm,Participant.Publisher) => next=Some(TaskStatus.Completed)
        case (TaskStatus.Pending,EventKind.Cancel,Participant.Publisher) => next=Some(TaskStatus.Cancelled)
        case (TaskStatus.InProgress,EventKind.Cancel,Participant.Acceptor) => next=Some(TaskStatus.Cancelled)
        case (TaskStatus.PendingConfirmation,EventKind.Cancel,Participant.Acceptor) => next=Some(TaskStatus.Cancelled)
        case _ => ()
    }
    if(let Some(status) <- next) {return Decision("APPLIED",status,snapshot.version+1)}
    return Decision("STATE_OR_ROLE_REJECTED",snapshot.status,snapshot.version)
}
struct Memo {
    let fingerprint: String
    let result: Decision
    init(fingerprint: String, result: Decision) {this.fingerprint=fingerprint;this.result=result}
}
class EventProjection {
    private let lock=Mutex()
    private let snapshots=HashMap<Int64,TaskSnapshot>()
    private let seen=HashMap<String,Memo>()
    init(tasks: ArrayList<TaskSnapshot>) {
        for(task in tasks) {
            if(snapshots.contains(task.id)) {throw IllegalArgumentException("DUPLICATE_TASK")}
            snapshots[task.id]=task
        }
    }
    func apply(event: TaskEvent): Decision {
        synchronized(lock) {
            if(!snapshots.contains(event.taskId)) {throw IllegalArgumentException("TASK_MISSING")}
            let task=snapshots[event.taskId]
            let key="${event.taskId}:${event.id}"
            if(seen.contains(key)) {
                let previous=seen[key]
                if(previous.fingerprint!=event.fingerprint()) {return Decision("EVENT_ID_CONFLICT",task.status,task.version)}
                return Decision("REPLAY",previous.result.status,previous.result.version)
            }
            let result=reduce(task,event)
            seen[key]=Memo(event.fingerprint(),result)
            if(result.outcome=="APPLIED") {snapshots[task.id]=TaskSnapshot(task.id,task.category,result.status,result.version)}
            return result
        }
    }
}
