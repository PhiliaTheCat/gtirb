from io import BytesIO
from typing import Any, ClassVar, Dict, Optional
from uuid import UUID

from .node import Node
from .proto import AuxData_pb2
from .serialization import Serialization
from .util import DictLike


class _LazyDataContainer:
    """
    Container that holds the raw byte stream until it is read, then releases
    it. If it is never read, then serialization skips re-encoding (and
    deserializing) the data.
    """

    def __init__(self, raw_data, type_name, get_by_uuid):
        self.raw_data = raw_data
        self.type_name = type_name
        self.get_by_uuid = get_by_uuid

    def get_data(self):
        # type: (Any) -> Any
        """
        Get any pending still-serialized data, or return the passed data
        instead (the default).
        """
        assert self.raw_data is not None
        rv = AuxData.serializer.decode(
            self.raw_data, self.type_name, self.get_by_uuid
        )
        self.raw_data = None
        return rv

    def get_raw_data(self):
        # type: () -> bytes
        """
        """
        assert self.raw_data is not None
        return self.raw_data


class AuxData:
    """AuxData objects can be attached to the :class:`gtirb.IR` or individual
    :class:`gtirb.Module` s to store additional client-specific data in a
    portable way.

    AuxData represents a portable, language-independent manner of encoding
    rich data. To do this, all data is stored on disk as a series of bytes
    with a string describing the format of the data, called a *type name*. See
    :mod:`gtirb.serialization` for the list of all default types. Types may
    also be parameterized; for example, ``mapping<string,UUID>`` is a ``dict``
    from ``str`` objects to ``UUID`` objects. All ``AuxData`` requires
    a valid type name in order to be serialized.

    :ivar ~.data: The value stored in this AuxData.
    :ivar ~.type_name: A string describing the type of ``data``.
        Used to determine the proper codec for serializing this AuxData.
    """

    serializer = Serialization()  # type: ClassVar[Serialization]
    """This is a :class:`gtirb.Serialization` instance, used to
    encode and decode ``data`` fields of all ``AuxData``. See
    :mod:`gtirb.serialization` for details.
    """

    def __init__(self, data, type_name, lazy_container=None):
        # type: (Any, str, Optional[_LazyDataContainer]) -> None
        """
        :param data: The value stored in this AuxData.
        :param type_name: A string describing the type of ``data``.
            Used to determine the proper codec for serializing this AuxData.
        :param lazy_container: An object that will lazily deserialize the
            auxdata table backing this object, or None.
        """
        self._lazy_container = lazy_container
        self._data = data  # type: Any
        self.type_name = type_name  # type: str

    @property
    def data(self):
        if self._lazy_container is not None:
            self._data = self._lazy_container.get_data()
            self._lazy_container = None
        return self._data

    @data.setter
    def data(self, value):
        self._data = value
        self._lazy_container = None

    @classmethod
    def _from_protobuf(cls, aux_data, ir):
        # type: (AuxData_pb2.AuxData, Optional["IR"]) -> AuxData
        """Deserialize AuxData from Protobuf. Lazy, will not perform
        deserialization until .data is accessed.

        :param aux_data: The Protobuf AuxData object.
        """

        # Defer deserialization until someone accesses .data
        lazy_container = _LazyDataContainer(
            aux_data.data, aux_data.type_name, ir.get_by_uuid
        )
        return cls(
            data=None,
            type_name=aux_data.type_name,
            lazy_container=lazy_container,
        )

    def _to_protobuf(self):
        # type: () -> AuxData_pb2.AuxData
        """Get a Protobuf representation of the AuxData."""

        proto_auxdata = AuxData_pb2.AuxData()
        proto_auxdata.type_name = self.type_name
        if self._lazy_container is not None:
            proto_auxdata.data = self._lazy_container.get_raw_data()
        else:
            data_stream = BytesIO()
            AuxData.serializer.encode(data_stream, self.data, self.type_name)
            proto_auxdata.data = data_stream.getvalue()
        return proto_auxdata

    def __repr__(self):
        # type: () -> str
        return (
            "AuxData("
            "type_name={type_name!r}, "
            "data={data!r}, "
            ")".format(type_name=self.type_name, data=self.data)
        )


class AuxDataContainer(Node):
    """The base class for anything that holds AuxData tables; that is,
    :class:`gtirb.IR` and :class:`gtirb.Module`.

    :ivar ~.aux_data: The auxiliary data associated
            with the object, as a mapping from names to
            :class:`gtirb.AuxData`.
    """

    def __init__(
        self,
        aux_data={},  # type: DictLike[str, AuxData]
        uuid=None,  # type: Optional[UUID]
    ):
        # type: (...) -> None
        """
        :param aux_data: The initial auxiliary data to be associated
            with the object, as a mapping from names to
            :class:`gtirb.AuxData`. Defaults to an empty :class:`dict`.
        :param uuid: the UUID of this ``AuxDataContainer``,
            or None if a new UUID needs generated via :func:`uuid.uuid4`.
            Defaults to None.
        """
        super().__init__(uuid)
        self.aux_data = dict(aux_data)  # type: Dict[str, AuxData]

    @classmethod
    def _read_protobuf_aux_data(cls, proto_container, ir):
        # type: (Any,Optional["IR"]) -> Dict[str, AuxData]
        """
        Instead of the overrided _decode_protobuf, this method requires the
        Protobuf message to read from. AuxDataContainers need to call this
        method in their own _decode_protobuf overrides.

        :param proto_container: A Protobuf message with a field called
            ``aux_data``.
        """
        return {
            key: AuxData._from_protobuf(val, ir)
            for key, val in proto_container.aux_data.items()
        }

    def _write_protobuf_aux_data(self, proto_container):
        # type: (Any) -> None
        """
        Instead of the overrided _to_protobuf, this method requires the
        Protobuf message to write into. AuxDataContainers need to call this
        method in their own _to_protobuf overrides.

        :param proto_container: A Protobuf message with a field called
            ``aux_data``.
        """
        for k, v in self.aux_data.items():
            proto_container.aux_data[k].CopyFrom(v._to_protobuf())

    def deep_eq(self, other):
        # type: (Any) -> bool
        """This overrides :func:`gtirb.Node.deep_eq` to check for
        AuxData equality.

        Because the values stored by AuxData are not necessarily
        amenable to deep checking, the auxiliary data dictionaries
        stored for ``self`` and ``other`` are not deeply checked. Instead,
        they are considered to be equal if their sets of keys are equal.
        """

        if not isinstance(other, AuxDataContainer):
            return False
        if (
            self.uuid != other.uuid
            or self.aux_data.keys() != other.aux_data.keys()
        ):
            return False
        return True
