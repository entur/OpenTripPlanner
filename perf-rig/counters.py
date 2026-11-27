"""
The CPU counters (instructions, cycles, cache misses, ...) that a thread used, from a Windows event
trace that has the counters at every context switch (recorded with counters.wprp).

At a context switch on a processor, the trace has the counters of that processor. The difference
from the switch before on the same processor is what the thread that was switched out used.
"""
import ctypes
import sys
from ctypes import wintypes

THREAD_EVENTS = "3d6fa8d1-fe05-11d0-9dda-00c04fd7ba7c"  # the kernel events of threads
CONTEXT_SWITCH = 36  # its opcode of a context switch
PROFILING_EVENTS = "ce1dbfb4-137e-4da6-87b0-3f59aa102cbc"  # the kernel events of profiling
COUNTER_NAMES = 48  # its opcode of the event that names the counters: a count, then the names
PMC_COUNTERS = 8  # the type of the extended data of an event that has the counters
EPOCH_AS_FILETIME = 116444736000000000  # 1970-01-01 in units of 100 ns since 1601-01-01


class GUID(ctypes.Structure):
    _fields_ = [("Data1", wintypes.DWORD), ("Data2", wintypes.WORD), ("Data3", wintypes.WORD),
                ("Data4", ctypes.c_ubyte * 8)]

    def __str__(self):
        tail = bytes(self.Data4).hex()
        return f"{self.Data1:08x}-{self.Data2:04x}-{self.Data3:04x}-{tail[:4]}-{tail[4:]}"


class EventDescriptor(ctypes.Structure):
    _fields_ = [("Id", wintypes.USHORT), ("Version", ctypes.c_ubyte), ("Channel", ctypes.c_ubyte),
                ("Level", ctypes.c_ubyte), ("Opcode", ctypes.c_ubyte), ("Task", wintypes.USHORT),
                ("Keyword", ctypes.c_ulonglong)]


class EventHeader(ctypes.Structure):
    _fields_ = [("Size", wintypes.USHORT), ("HeaderType", wintypes.USHORT), ("Flags", wintypes.USHORT),
                ("EventProperty", wintypes.USHORT), ("ThreadId", wintypes.ULONG), ("ProcessId", wintypes.ULONG),
                ("TimeStamp", ctypes.c_longlong), ("ProviderId", GUID), ("EventDescriptor", EventDescriptor),
                ("ProcessorTime", ctypes.c_ulonglong), ("ActivityId", GUID)]


class ExtendedDataItem(ctypes.Structure):
    _fields_ = [("Reserved1", wintypes.USHORT), ("ExtType", wintypes.USHORT), ("Linkage", wintypes.USHORT),
                ("DataSize", wintypes.USHORT), ("DataPtr", ctypes.c_ulonglong)]


class EventRecord(ctypes.Structure):
    _fields_ = [("EventHeader", EventHeader), ("ProcessorNumber", ctypes.c_ubyte), ("Alignment", ctypes.c_ubyte),
                ("LoggerId", wintypes.USHORT), ("ExtendedDataCount", wintypes.USHORT),
                ("UserDataLength", wintypes.USHORT), ("ExtendedData", ctypes.POINTER(ExtendedDataItem)),
                ("UserData", ctypes.c_void_p), ("UserContext", ctypes.c_void_p)]


EVENT_CALLBACK = ctypes.WINFUNCTYPE(None, ctypes.POINTER(EventRecord))


class EventTraceLogfile(ctypes.Structure):
    # EVENT_TRACE_LOGFILEW; the current event (88 bytes) and the header of the file (280 bytes) are not read
    _fields_ = [("LogFileName", wintypes.LPWSTR), ("LoggerName", wintypes.LPWSTR), ("CurrentTime", ctypes.c_longlong),
                ("BuffersRead", wintypes.ULONG), ("ProcessTraceMode", wintypes.ULONG),
                ("CurrentEvent", ctypes.c_ubyte * 88), ("LogfileHeader", ctypes.c_ubyte * 280),
                ("BufferCallback", ctypes.c_void_p), ("BufferSize", wintypes.ULONG), ("Filled", wintypes.ULONG),
                ("EventsLost", wintypes.ULONG), ("EventRecordCallback", EVENT_CALLBACK),
                ("IsKernelTrace", wintypes.ULONG), ("Context", ctypes.c_void_p)]


def context_switches(trace_file):
    """
    The names of the counters of a trace file, and its context switches in the order of time, as
    (time in ns since the epoch, processor, thread that was switched out, counters of the
    processor). Reading a trace needs no administrator rights; recording one does.
    """
    names, switches = [], []

    def on_event(record_pointer):
        record = record_pointer.contents
        header = record.EventHeader
        opcode, provider = header.EventDescriptor.Opcode, str(header.ProviderId)
        if opcode == COUNTER_NAMES and provider == PROFILING_EVENTS:
            data = ctypes.string_at(record.UserData, record.UserDataLength)
            count = int.from_bytes(data[:4], "little")
            names[:] = data[4:].decode("utf-16-le").split("\0")[:count]
        if opcode != CONTEXT_SWITCH or provider != THREAD_EVENTS:
            return
        for k in range(record.ExtendedDataCount):
            item = record.ExtendedData[k]
            if item.ExtType == PMC_COUNTERS:
                counters = (ctypes.c_ulonglong * (item.DataSize // 8)).from_address(item.DataPtr)
                old_thread = ctypes.c_uint32.from_address(record.UserData + 4).value
                nanos = (header.TimeStamp - EPOCH_AS_FILETIME) * 100
                switches.append((nanos, record.ProcessorNumber, old_thread, tuple(counters)))

    advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)
    advapi32.OpenTraceW.restype = ctypes.c_ulonglong
    advapi32.OpenTraceW.argtypes = [ctypes.POINTER(EventTraceLogfile)]
    advapi32.ProcessTrace.argtypes = [ctypes.POINTER(ctypes.c_ulonglong), wintypes.ULONG, ctypes.c_void_p, ctypes.c_void_p]
    advapi32.CloseTrace.argtypes = [ctypes.c_ulonglong]
    logfile = EventTraceLogfile()
    logfile.LogFileName = str(trace_file)
    logfile.ProcessTraceMode = 0x10000000  # the callback gets event records
    callback = EVENT_CALLBACK(on_event)
    logfile.EventRecordCallback = callback
    handle = advapi32.OpenTraceW(ctypes.byref(logfile))
    if handle in (0xFFFFFFFFFFFFFFFF, 0xFFFFFFFF):
        raise OSError(f"Can not open the trace {trace_file}: error {ctypes.get_last_error()}")
    try:
        error = advapi32.ProcessTrace(ctypes.byref(ctypes.c_ulonglong(handle)), 1, None, None)
        if error:
            raise OSError(f"Can not read the trace {trace_file}: error {error}")
    finally:
        advapi32.CloseTrace(handle)
    # A switch that is missing gives its work to another thread. In the header of the file: the
    # events lost (at byte 48) and the buffers lost (at byte 276).
    lost = sum(int.from_bytes(bytes(logfile.LogfileHeader[at:at + 4]), "little") for at in (48, 276))
    if lost or logfile.EventsLost:
        raise OSError(f"The trace {trace_file} lost events: the counters of the threads are not right")
    return names, switches


def used_by_thread(switches, thread, windows):
    """
    The counters a thread used in each named list of (start, end) windows, in ns since the epoch:
    name -> the sum of each counter, None if the thread did not run there. What the thread used
    until a switch counts in the window the switch is in.
    """
    before = {}
    used = {name: None for name in windows}
    for nanos, processor, old_thread, counters in switches:
        last = before.get(processor)
        before[processor] = counters
        if last is None or old_thread != thread:
            continue
        for name, spans in windows.items():
            if any(start <= nanos <= end for start, end in spans):
                sums = used[name] or [0] * len(counters)
                used[name] = [s + c - b for s, c, b in zip(sums, counters, last)]
    return used


def record_on_request(directory, client):
    """
    Record traces when asked to, until the client process is gone: this runs with administrator
    rights, which recording needs, so that the benchmark itself does not. The client writes the
    file 'start' or 'stop' (with the path of the trace file) in the directory and waits for
    'started' or 'stopped'; an error is the text of the file 'failed'.
    """
    import subprocess
    import time
    from pathlib import Path
    directory = Path(directory)
    profile = Path(__file__).with_name("counters.wprp")
    recording = False

    def wpr(*args):
        run = subprocess.run(["wpr", *args], capture_output=True, text=True)
        if run.returncode:
            (directory / "failed").write_text((run.stdout + run.stderr).strip() or f"wpr {args[0]} failed")
        return run.returncode == 0

    kernel32 = ctypes.windll.kernel32
    kernel32.OpenProcess.restype = ctypes.c_void_p
    process = kernel32.OpenProcess(0x00100000, False, client)  # the right to wait for the process
    (directory / "ready").write_text("")
    # 258: the wait timed out, so the client is still there
    while kernel32.WaitForSingleObject(ctypes.c_void_p(process), 200) == 258:
        if (directory / "start").exists() and not recording:
            (directory / "start").unlink()
            recording = wpr("-start", str(profile), "-filemode")
            if recording:
                (directory / "started").write_text("")
        if (directory / "stop").exists() and recording:
            trace = (directory / "stop").read_text()
            (directory / "stop").unlink()
            recording = False
            if wpr("-stop", trace):
                (directory / "stopped").write_text("")
        time.sleep(0.1)
    if recording:
        wpr("-cancel")  # the client is gone: do not record on


if __name__ == "__main__" and sys.argv[1] == "record":
    # counters.py record DIRECTORY CLIENT_PID, started with administrator rights by rig.py
    record_on_request(sys.argv[2], int(sys.argv[3]))
elif __name__ == "__main__":
    # counters.py TRACE: what the trace has, to look at a recording
    counter_names, found = context_switches(sys.argv[1])
    print(f"{len(found)} context switches with the counters {counter_names}")
    if found:
        print(f"from {found[0][0]} to {found[-1][0]} ns, {(found[-1][0] - found[0][0]) / 1e9:.1f} s, "
              f"processors {sorted({s[1] for s in found})}, {len(found[0][3])} counters")
        totals = {}
        last_of = {}
        for nanos, processor, old_thread, counters in found:
            if processor in last_of:
                sums = totals.setdefault(old_thread, [0] * len(counters))
                for k, (c, b) in enumerate(zip(counters, last_of[processor])):
                    sums[k] += c - b
            last_of[processor] = counters
        for thread in sorted(totals, key=lambda t: totals[t][0], reverse=True)[:8]:
            print(f"  thread {thread:6}: " + "  ".join(f"{v:.4g}" for v in totals[thread]))
